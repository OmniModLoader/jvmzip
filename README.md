# JVM-Zip

> [!WARNING]
> This is an internal library and not recommended for general use.

This is a custom Zip parser meant for speed. It only supports what the JVM is capable of loading in.
However, I do not have access to Zip64 files so that feature will be added when I do.

## What does this thing support?
Well i'm glad you asked, almost no built-in Zip options.
As I quote it from the official source: ["A JAR file is essentially a zip file that contains an optional META-INF directory"](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html).

The JVM supports Zip64 however I do not know where one can get such a massive Zip file, so I can't test it therefor I can't make it.
But we do support Zip32 which is about <4GB. The only compression it supports is Deflate, because guess what the JVM only supports Deflate and not compressed.

If I am wrong about the compression thing please tell me, I only found that by testing. But if you know something I don't i'd rather be proven wrong than have bad code.

## Usage
Once again using this library is not really recommended as it was made at 3AM, after
spending hours trying to decipher the hell that is [APPNOTE.TXT](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT).

But if you want to use it here is how:

```java
import java.io.File;
import java.io.IOException;

import org.omnimc.jvmzip.ZipParser;
import org.omnimc.jvmzip.entry.ZipEntry;

public static void main(String[] args) throws IOException {
    File file = new File("weird.zip");
    ZipParser parser = new ZipParser(file); // This can take a String, URI or a File
    ZipEntry entry = parser.getEntry("weird.txt");
    parser.close(); // close to make sure resources are released, just don't middle-click it.
    // Here you can access the methods in ZipEntry to get anything out of the entry.
}
```

It is a very simple dumb down Zip library so if you want speed > features, go right ahead.

## Contributing
If you feel the need to help me in my efforts of understanding the rambles of a mad man, that is [APPNOTE.TXT](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT).
Please remember optimization is our main focus here, pull out all the stops even if it makes the code look garbage.

Every line of code contributed to OmniMC is appreciated.

## Why?
I made this because of how unoptimized ZipInputStream was for my use cases.
I wanted a dumbed down Zip Parser that followed only what Jar files are capable of.
The Zip File specification is very annoying to deal with, and the amount of "options" it can support
makes ZipInputStream really slow, you can really notice it when reading large zips.
However, the JVM does not support half of those options, and only one type of compression.