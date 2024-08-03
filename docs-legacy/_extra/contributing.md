---
layout: redirect
redirect_to: https://opensource.deepmedia.io/transcoder
title: "Contributing & License"
order: 1
---

Everyone is welcome to contribute with suggestions or pull requests, as the library is under active development.

We are grateful to anyone who will contribute with fixes, features or feature requests. If you don't
want to get involved but still want to support the project, please [consider donating](donate).

### Bug reports

Please make sure to fill the bug report issue template on GitHub, if applicable.
We highly recommend to try to reproduce the bug in the demo app, as this helps a lot in debugging
and excludes programming errors from your side.

Make sure to include:

- A clear and concise description of what the bug is
- Transcoder version, device type, Android API level
- Exact steps to reproduce the issue
- Description of the expected behavior
- The original media file(s) that manifest the problem

Recommended extras:

- LogCat logs (use `Logger.setLogLevel(LEVEL_VERBOSE)` to print all)
- Link to a GitHub repo where the bug is reproducible

### Pull Requests

Please open an issue first!

Unless your PR is a simple fix (typos, documentation, bugs with obvious solution), opening an issue
will let us discuss the problem, take design decisions and have a reference to the issue description.

If you can, please write tests. We are planning to work on improving the library test coverage soon.

### License

This project is licensed under Apache 2.0. It consists of improvements over
the [ypresto/android-transcoder](https://github.com/ypresto/android-transcoder)
project which was licensed under Apache 2.0 as well:

```
Copyright (C) 2014-2016 Yuya Tanaka

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
