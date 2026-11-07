import {
  ShaderMount,
  halftoneCmykFragmentShader,
  getShaderColorFromString,
  getShaderNoiseTexture,
} from './node_modules/@paper-design/shaders/dist/index.js';

// Tuned interactively at shaders.paper.design/halftone-cmyk. Keep this preset
// explicit so the final artwork is reproducible without an image generator.
const color = getShaderColorFromString;
const image = new Image();
image.src = './flowers.webp';
await image.decode();
const noise = getShaderNoiseTexture();
await noise.decode();
const mount = new ShaderMount(document.querySelector('#shader'), halftoneCmykFragmentShader, {
  u_image: image,
  u_imageAspectRatio: image.naturalWidth / image.naturalHeight,
  u_noiseTexture: noise,
  u_colorBack: color('#0d1117'),
  u_colorC: color('#3abff8'),
  u_colorM: color('#2463eb'),
  u_colorY: color('#7ac1ff'),
  u_colorK: color('#231f20'),
  u_size: 0.38,
  u_gridNoise: 0.3,
  u_type: 0,
  u_softness: 0.3,
  u_contrast: 1,
  u_floodC: 0, u_floodM: 0, u_floodY: 0, u_floodK: 0,
  u_gainC: 0.3, u_gainM: 0, u_gainY: 0.2, u_gainK: 0,
  u_grainMixer: 0.04, u_grainOverlay: 0, u_grainSize: 0.5,
  u_fit: 2, u_scale: 1, u_rotation: 0,
  u_offsetX: 0, u_offsetY: 0,
  u_originX: 0.5, u_originY: 0.5,
  u_worldWidth: 0, u_worldHeight: 0,
}, { preserveDrawingBuffer: true }, 0, 0, 1, 1600 * 640);

// Wait for the mount's resize observation and first WebGL frame.
await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
mount.setFrame(0);
const canvas = document.querySelector('#banner');
const ctx = canvas.getContext('2d');
const { width, height } = canvas;
ctx.fillStyle = '#0d1117';
ctx.fillRect(0, 0, width, height);
ctx.drawImage(mount.canvasElement, 0, 0, width, height);

// A quiet center for legible text; keep the halftone flowers visible around it.
const center = ctx.createRadialGradient(800, 315, 40, 800, 315, 650);
center.addColorStop(0, 'rgba(13,17,23,0.88)');
center.addColorStop(0.55, 'rgba(13,17,23,0.58)');
center.addColorStop(1, 'rgba(13,17,23,0)');
ctx.fillStyle = center;
ctx.fillRect(0, 0, width, height);

// Fade to the exact GitHub dark color on every outer edge.
for (const [x0, y0, x1, y1] of [[0,0,120,0], [width,0,width-120,0], [0,0,0,60], [0,height,0,height-60]]) {
  const edge = ctx.createLinearGradient(x0, y0, x1, y1);
  edge.addColorStop(0, '#0d1117');
  edge.addColorStop(1, 'rgba(13,17,23,0)');
  ctx.fillStyle = edge;
  ctx.fillRect(0, 0, width, height);
}

ctx.textAlign = 'center';
ctx.fillStyle = '#f0f6fc';
ctx.font = '700 128px Arial, sans-serif';
ctx.fillText('ShaderX', 800, 290);
ctx.font = '400 42px Arial, sans-serif';
ctx.fillText('GPU shader effects', 800, 365);
ctx.fillStyle = '#b6d7ff';
ctx.font = '400 34px Arial, sans-serif';
ctx.fillText('Kotlin & Compose Multiplatform', 800, 420);

const download = document.querySelector('#download');
download.href = canvas.toDataURL('image/png');
document.querySelector('#status').textContent = 'Ready · 1600 × 640 · Paper Shaders 0.0.82 · WebGL render';
const save = document.querySelector('#save');
save.disabled = false;
save.addEventListener('click', async () => {
  const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
  const response = await fetch('/export', { method: 'POST', headers: { 'Content-Type': 'image/png' }, body: blob });
  document.querySelector('#status').textContent = response.ok ? await response.text() : 'Export failed. Start the renderer with python3 serve.py.';
});
mount.dispose();
