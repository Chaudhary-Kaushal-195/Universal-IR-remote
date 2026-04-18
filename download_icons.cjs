const fs = require('fs');
const https = require('https');

const download = (url, dest) => {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    https.get(url, { headers: { 'User-Agent': 'Node' } }, (response) => {
      if (response.statusCode === 301 || response.statusCode === 302) {
        return download(response.headers.location, dest).then(resolve).catch(reject);
      }
      response.pipe(file);
      file.on('finish', () => resolve());
    }).on('error', (err) => reject(err));
  });
};

Promise.all([
  download('https://dummyimage.com/192x192/6366f1/ffffff.png&text=Hub', 'public/pwa-192x192.png'),
  download('https://dummyimage.com/512x512/6366f1/ffffff.png&text=Hub', 'public/pwa-512x512.png')
]).then(() => console.log('Icons downloaded successfully!'));
