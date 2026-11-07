// Register a root Mocha hook before Kotlin's generated loader starts the tests.
// The setup imports the same bundled skiko.mjs as the Kotlin/Wasm test module.
(function (config) {
    const path = require("path");
    const setup = path.resolve(config.basePath, "kotlin", "skiko-test-setup.mjs");
    config.files.unshift(setup);
    config.preprocessors[setup] = ["webpack"];
})(config);
