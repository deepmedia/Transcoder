package com.otaliastudios.transcoder.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class WavFile {
    private enum IOState {READING, WRITING, CLOSED}

    private final static int BUFFER_SIZE = 4096;

    private final static int FMT_CHUNK_ID = 0x20746D66;
    private final static int DATA_CHUNK_ID = 0x61746164;
    private final static int RIFF_CHUNK_ID = 0x46464952;
    private final static int RIFF_TYPE_ID = 0x45564157;

    private File file;                        // File that will be read from or written to
    private IOState ioState;                // Specifies the IO State of the Wav File (used for snaity checking)
    private int bytesPerSample;            // Number of bytes required to store a single sample
    private long numFrames;                    // Number of frames within the data section
    private FileOutputStream oStream;    // Output stream used for writting data
    private FileInputStream iStream;        // Input stream used for reading data
    private float floatScale;                // Scaling factor used for int <-> float conversion
    private float floatOffset;            // Offset factor used for int <-> float conversion
    private boolean wordAlignAdjust;        // Specify if an extra byte at the end of the data chunk is required for word alignment

    // Wav Header
    private int numChannels;                // 2 bytes unsigned, 0x0001 (1) to 0xFFFF (65,535)
    private long sampleRate;                // 4 bytes unsigned, 0x00000001 (1) to 0xFFFFFFFF (4,294,967,295)
    // Although a java int is 4 bytes, it is signed, so need to use a long
    private int blockAlign;                    // 2 bytes unsigned, 0x0001 (1) to 0xFFFF (65,535)
    private int validBits;                    // 2 bytes unsigned, 0x0002 (2) to 0xFFFF (65,535)

    // Buffering
    private byte[] buffer;                    // Local buffer used for IO
    private int bufferPointer;                // Points to the current position in local buffer
    private int bytesRead;                    // Bytes read after last read into local buffer
    private long frameCounter;                // Current number of frames read or written
    private long fileSize;

    // Cannot instantiate WavFile directly, must either use newWavFile() or openWavFile()
    private WavFile() {
        buffer = new byte[BUFFER_SIZE];
    }

    public int getNumChannels() {
        return numChannels;
    }

    public long getNumFrames() {
        return numFrames;
    }

    public long getFramesRemaining() {
        return numFrames - frameCounter;
    }

    public long getSampleRate() {
        return sampleRate;
    }

    public int getValidBits() {
        return validBits;
    }

    public long getDuration() {
        return getNumFrames() / getSampleRate();
    }

    public long getFileSize() {
        return fileSize;
    }

    public static WavFile openWavFile(File file) throws Exception {
        // Instantiate new Wavfile and store the file reference
        WavFile wavFile = new WavFile();
        wavFile.file = file;

        // Create a new file input stream for reading file data
        wavFile.iStream = new FileInputStream(file);

        // Read the first 12 bytes of the file
        int bytesRead = wavFile.iStream.read(wavFile.buffer, 0, 12);
        if (bytesRead != 12) throw new Exception("Not enough wav file bytes for header");

        // Extract parts from the header
        long riffChunkID = getLE(wavFile.buffer, 0, 4);
        long chunkSize = getLE(wavFile.buffer, 4, 4);
        long riffTypeID = getLE(wavFile.buffer, 8, 4);

        // Check the header bytes contains the correct signature
        if (riffChunkID != RIFF_CHUNK_ID)
            throw new Exception("Invalid Wav Header data, incorrect riff chunk ID");
        if (riffTypeID != RIFF_TYPE_ID) throw new Exception("Invalid Wav Header data, incorrect riff type ID");

        // Check that the file size matches the number of bytes listed in header
        if (file.length() != chunkSize + 8) {
            throw new Exception("Header chunk size (" + chunkSize + ") does not match file size (" + file.length() + ")");
        }

        wavFile.fileSize = chunkSize;

        boolean foundFormat = false;
        boolean foundData = false;

        // Search for the Format and Data Chunks
        while (true) {
            // Read the first 8 bytes of the chunk (ID and chunk size)
            bytesRead = wavFile.iStream.read(wavFile.buffer, 0, 8);
            if (bytesRead == -1) throw new Exception("Reached end of file without finding format chunk");
            if (bytesRead != 8) throw new Exception("Could not read chunk header");

            // Extract the chunk ID and Size
            long chunkID = getLE(wavFile.buffer, 0, 4);
            chunkSize = getLE(wavFile.buffer, 4, 4);

            // Word align the chunk size
            // chunkSize specifies the number of bytes holding data. However,
            // the data should be word aligned (2 bytes) so we need to calculate
            // the actual number of bytes in the chunk
            long numChunkBytes = (chunkSize % 2 == 1) ? chunkSize + 1 : chunkSize;

            if (chunkID == FMT_CHUNK_ID) {
                // Flag that the format chunk has been found
                foundFormat = true;

                // Read in the header info
                bytesRead = wavFile.iStream.read(wavFile.buffer, 0, 16);

                // Check this is uncompressed data
                int compressionCode = (int) getLE(wavFile.buffer, 0, 2);
                if (compressionCode != 1)
                    throw new Exception("Compression Code " + compressionCode + " not supported");

                // Extract the format information
                wavFile.numChannels = (int) getLE(wavFile.buffer, 2, 2);
                wavFile.sampleRate = getLE(wavFile.buffer, 4, 4);
                wavFile.blockAlign = (int) getLE(wavFile.buffer, 12, 2);
                wavFile.validBits = (int) getLE(wavFile.buffer, 14, 2);

                if (wavFile.numChannels == 0)
                    throw new Exception("Number of channels specified in header is equal to zero");
                if (wavFile.blockAlign == 0)
                    throw new Exception("Block Align specified in header is equal to zero");
                if (wavFile.validBits < 2) throw new Exception("Valid Bits specified in header is less than 2");
                if (wavFile.validBits > 64)
                    throw new Exception("Valid Bits specified in header is greater than 64, this is greater than a long can hold");

                // Calculate the number of bytes required to hold 1 sample
                wavFile.bytesPerSample = (wavFile.validBits + 7) / 8;
                if (wavFile.bytesPerSample * wavFile.numChannels != wavFile.blockAlign)
                    throw new Exception("Block Align does not agree with bytes required for validBits and number of channels");

                // Account for number of format bytes and then skip over
                // any extra format bytes
                numChunkBytes -= 16;
                if (numChunkBytes > 0) wavFile.iStream.skip(numChunkBytes);
            } else if (chunkID == DATA_CHUNK_ID) {
                // Check if we've found the format chunk,
                // If not, throw an exception as we need the format information
                // before we can read the data chunk
                if (foundFormat == false) throw new Exception("Data chunk found before Format chunk");

                // Check that the chunkSize (wav data length) is a multiple of the
                // block align (bytes per frame)
                if (chunkSize % wavFile.blockAlign != 0)
                    throw new Exception("Data Chunk size is not multiple of Block Align");

                // Calculate the number of frames
                wavFile.numFrames = chunkSize / wavFile.blockAlign;

                // Flag that we've found the wave data chunk
                foundData = true;

                break;
            } else {
                // If an unknown chunk ID is found, just skip over the chunk data
                wavFile.iStream.skip(numChunkBytes);
            }
        }

        // Throw an exception if no data chunk has been found
        if (foundData == false) throw new Exception("Did not find a data chunk");

        // Calculate the scaling factor for converting to a normalised double
        if (wavFile.validBits > 8) {
            // If more than 8 validBits, data is signed
            // Conversion required dividing by magnitude of max negative value
            wavFile.floatOffset = 0;
            wavFile.floatScale = 1 << (wavFile.validBits - 1);
        } else {
            // Else if 8 or less validBits, data is unsigned
            // Conversion required dividing by max positive value
            wavFile.floatOffset = -1;
            wavFile.floatScale = 0.5f * ((1 << wavFile.validBits) - 1);
        }

        wavFile.bufferPointer = 0;
        wavFile.bytesRead = 0;
        wavFile.frameCounter = 0;
        wavFile.ioState = IOState.READING;

        return wavFile;
    }

    private static long getLE(byte[] buffer, int pos, int numBytes) {
        numBytes--;
        pos += numBytes;

        long val = buffer[pos] & 0xFF;
        for (int b = 0; b < numBytes; b++) val = (val << 8) + (buffer[--pos] & 0xFF);

        return val;
    }

    private long readSample() throws IOException, Exception {
        long val = 0;

        for (int b = 0; b < bytesPerSample; b++) {
            if (bufferPointer == bytesRead) {
                int read = iStream.read(buffer, 0, BUFFER_SIZE);
                if (read == -1) throw new Exception("Not enough data available");
                bytesRead = read;
                bufferPointer = 0;
            }

            int v = buffer[bufferPointer];
            if (b < bytesPerSample - 1 || bytesPerSample == 1) v &= 0xFF;
            val += v << (b * 8);

            bufferPointer++;
        }

        return val;
    }

    public int readFrames(float[] sampleBuffer, int numFramesToRead) throws IOException, Exception {
        return readFramesInternal(sampleBuffer, 0, numFramesToRead);
    }

    private int readFramesInternal(float[] sampleBuffer, int offset, int numFramesToRead) throws IOException, Exception {
        if (ioState != IOState.READING) throw new IOException("Cannot read from WavFile instance");

        for (int f = 0; f < numFramesToRead; f++) {
            if (frameCounter == numFrames) return f;

            for (int c = 0; c < numChannels; c++) {
                sampleBuffer[offset] = floatOffset + (float) readSample() / floatScale;
                offset++;
            }

            frameCounter++;
        }

        return numFramesToRead;
    }

    public int readFrames(int[] sampleBuffer, int numFramesToRead) throws IOException, Exception {
        return readFramesInternal(sampleBuffer, 0, numFramesToRead);
    }

    private int readFramesInternal(int[] sampleBuffer, int offset, int numFramesToRead) throws IOException, Exception {
        if (ioState != IOState.READING) throw new IOException("Cannot read from WavFile instance");

        for (int f = 0; f < numFramesToRead; f++) {
            if (frameCounter == numFrames) return f;

            for (int c = 0; c < numChannels; c++) {
                sampleBuffer[offset] = (int) readSample();
                offset++;
            }

            frameCounter++;
        }

        return numFramesToRead;
    }

    public void close() throws IOException {
        // Close the input stream and set to null
        if (iStream != null) {
            iStream.close();
            iStream = null;
        }

        if (oStream != null) {
            // Write out anything still in the local buffer
            if (bufferPointer > 0) oStream.write(buffer, 0, bufferPointer);

            // If an extra byte is required for word alignment, add it to the end
            if (wordAlignAdjust) oStream.write(0);

            // Close the stream and set to null
            oStream.close();
            oStream = null;
        }

        // Flag that the stream is closed
        ioState = IOState.CLOSED;
    }
}