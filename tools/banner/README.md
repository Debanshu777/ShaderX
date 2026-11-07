# Rebuild the ShaderX banner

This uses the real **Paper Halftone CMYK WebGL shader**, with settings tuned in
the [live editor](https://shaders.paper.design/halftone-cmyk#colorBack=0d1117&colorC=3abff8&colorM=2463eb&colorY=7ac1ff&colorK=231f20&size=0.38&gridNoise=0.3&type=dots&softness=0.3&contrast=1&floodC=0&floodM=0&floodY=0&floodK=0&gainC=0.3&gainM=0&gainY=0.2&gainK=0&grainMixer=0.04&grainOverlay=0&grainSize=0.5&scale=1&fit=cover).
It is a brand illustration, not a demo of a built-in ShaderX effect.

From this directory:

```shell
npm ci --ignore-scripts
python3 serve.py
```

Open [the local renderer](http://127.0.0.1:8876), wait for **Ready**, and click
**Save banner to repository** to replace `docs/assets/shaderx-banner.png`.
**Download PNG** also exports a standalone copy in browsers supporting downloads.
The export is 1600 × 640; it adds crisp type and fades to `#0d1117` after the
shader pass. No Node dependencies are needed to build the documentation site.

The source flower photograph is the sample shown in the user-supplied Paper
reference, exported from
[Paper's live editor](https://shaders.paper.design/images/image-filters/0018.webp).
`flowers.webp` keeps that source available for repeatable local rendering.
The shader dependency is [Paper Shaders](https://github.com/paper-design/shaders),
licensed under Apache 2.0; its package is pinned and installed from npm.
