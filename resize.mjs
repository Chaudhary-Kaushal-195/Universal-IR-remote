import { Jimp } from 'jimp';

async function resize() {
  const imagePath = "C:\\Users\\kaush\\.gemini\\antigravity\\brain\\ee3e1b22-3bf1-4dc1-a618-75a84f9dd108\\neon_remote_icon_1776542693972.png";
  try {
    const img = await Jimp.read(imagePath);
    img.resize({ w: 192, h: 192 });
    await img.write("public/logo-192.png");

    const img2 = await Jimp.read(imagePath);
    img2.resize({ w: 512, h: 512 });
    await img2.write("public/logo-512.png");
    
    const img3 = await Jimp.read(imagePath);
    img3.resize({ w: 180, h: 180 });
    await img3.write("public/logo-180.png");

    console.log("Resize complete!");
  } catch (err) {
    console.error(err);
  }
}
resize();
