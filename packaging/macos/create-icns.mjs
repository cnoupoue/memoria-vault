import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [iconsetDirectory, outputFile] = process.argv.slice(2);

if (!iconsetDirectory || !outputFile) {
  console.error('Usage: node create-icns.mjs <iconset-directory> <output.icns>');
  process.exit(1);
}

const iconEntries = [
  ['icp4', 'icon_16x16.png'],
  ['icp5', 'icon_32x32.png'],
  ['icp6', 'icon_32x32@2x.png'],
  ['ic07', 'icon_128x128.png'],
  ['ic08', 'icon_256x256.png'],
  ['ic09', 'icon_512x512.png'],
  ['ic10', 'icon_512x512@2x.png'],
];

const pngSignature = Buffer.from('89504e470d0a1a0a', 'hex');

function iconEntry(type, fileName) {
  const data = readFileSync(join(iconsetDirectory, fileName));

  if (!data.subarray(0, pngSignature.length).equals(pngSignature)) {
    throw new Error(`${fileName} is not a PNG file`);
  }

  const header = Buffer.alloc(8);
  header.write(type, 0, 4, 'ascii');
  header.writeUInt32BE(data.length + header.length, 4);

  return Buffer.concat([header, data]);
}

try {
  const entries = iconEntries.map(([type, fileName]) => iconEntry(type, fileName));
  const totalLength = entries.reduce((sum, entry) => sum + entry.length, 8);
  const header = Buffer.alloc(8);
  header.write('icns', 0, 4, 'ascii');
  header.writeUInt32BE(totalLength, 4);

  writeFileSync(outputFile, Buffer.concat([header, ...entries], totalLength));
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
