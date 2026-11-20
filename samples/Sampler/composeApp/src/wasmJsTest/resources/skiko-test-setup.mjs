import { awaitSkiko } from "./skiko.mjs";

// ImageBitmap construction calls native Skia exports, which are loaded asynchronously.
// A rejected runtime promise or timeout must fail the suite, not skip its assertions.
before(function () {
    this.timeout(30000);
    return awaitSkiko;
});
