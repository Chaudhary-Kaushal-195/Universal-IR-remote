import sharp from 'sharp';

async function resize() {
  const imagePath = "base_icon.png";
  try {
    await sharp(imagePath).resize(192, 192).png().toFile("public/logo-192.png");
    await sharp(imagePath).resize(512, 512).png().toFile("public/logo-512.png");
    await sharp(imagePath).resize(180, 180).png().toFile("public/logo-180.png");
    console.log("Resize complete!");
  } catch (err) {
    console.error(err);
  }
}
resize();
