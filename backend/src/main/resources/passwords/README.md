# Compromised-password list

`compromised-passwords.txt` is the offline blocklist of `BlocklistPasswordChecker` (docs/technical_analysis.md §5.1): a password found in it is refused at registration with `compromised_password`.

## Source

- **List:** `Passwords/Common-Credentials/Pwdb_top-1000000.txt` of [SecLists](https://github.com/danielmiessler/SecLists), the one million most common passwords of public breach compilations.
- **Version:** commit `12274c98fdebe98c7a7284914436a472ed469aed`, downloaded on 2026-11-06.
- **Licence:** MIT, notice below.

## How the file was made

Only entries the password policy could otherwise accept are kept; shorter or longer ones are already refused by the length rule:

1. NFKC normalisation of each line.
2. Kept if printable and 15 to 64 code points long.
3. Lower-cased, duplicates removed, original order kept.

Result: 5,328 entries (about 92 KB). The checker lower-cases the password with `Locale.ROOT` before looking it up, so the match is case-insensitive.

To refresh it, apply the same steps to a newer version of the list, and update the commit and the count above.

## Licence of the source list

```
MIT License

Copyright (c) 2018 Daniel Miessler

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
