const Jimp = require('jimp');

async function resize() {
  const imagePath = "C:\\Users\\kaush\\.gemini\\antigravity\\brain\\ee3e1b22-3bf1-4dc1-a618-75a84f9dd108\\neon_remote_icon_1776542693972.png";
  try {
    const img = await Jimp.read(imagePath);
    await img.clone().resize(192, 192).writeAsync('public/logo-192.png');
    await img.clone().resize(512, 512).writeAsync('public/logo-512.png');
    // Also generate a clean favicon/apple-touch-icon
    await img.clone().resize(180, 180).writeAsync('public/logo-180.png');
    console.log("Resize complete!");
  } catch (err) {
    console.error(err);
  }
}

resize();
