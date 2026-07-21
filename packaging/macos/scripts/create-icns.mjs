import { inflateSync } from 'node:zlib';
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

function parsePng(data, fileName) {
  if (!data.subarray(0, pngSignature.length).equals(pngSignature)) {
    throw new Error(`${fileName} is not a PNG file`);
  }

  let offset = pngSignature.length;
  let width = 0;
  let height = 0;
  let bitDepth = 0;
  let colorType = 0;
  const idatChunks = [];

  while (offset < data.length) {
    const length = data.readUInt32BE(offset);
    const type = data.toString('ascii', offset + 4, offset + 8);
    const chunkData = data.subarray(offset + 8, offset + 8 + length);
    offset += 12 + length;

    if (type === 'IHDR') {
      width = chunkData.readUInt32BE(0);
      height = chunkData.readUInt32BE(4);
      bitDepth = chunkData.readUInt8(8);
      colorType = chunkData.readUInt8(9);
    } else if (type === 'IDAT') {
      idatChunks.push(chunkData);
    } else if (type === 'IEND') {
      break;
    }
  }

  if (bitDepth !== 8 || colorType !== 6) {
    throw new Error(`${fileName} must be an 8-bit RGBA PNG to preserve icon transparency`);
  }

  return {
    width,
    height,
    pixels: unfilterRgbaPng(Buffer.concat(idatChunks), width, height, fileName),
  };
}

function unfilterRgbaPng(compressedData, width, height, fileName) {
  const bytesPerPixel = 4;
  const stride = width * bytesPerPixel;
  const inflated = inflateSync(compressedData);
  const expectedLength = (stride + 1) * height;

  if (inflated.length !== expectedLength) {
    throw new Error(`${fileName} has an unexpected PNG data length`);
  }

  const pixels = Buffer.alloc(stride * height);
  let sourceOffset = 0;

  for (let y = 0; y < height; y += 1) {
    const filter = inflated[sourceOffset];
    sourceOffset += 1;
    const rowOffset = y * stride;

    for (let x = 0; x < stride; x += 1) {
      const raw = inflated[sourceOffset + x];
      const left = x >= bytesPerPixel ? pixels[rowOffset + x - bytesPerPixel] : 0;
      const above = y > 0 ? pixels[rowOffset + x - stride] : 0;
      const upperLeft = y > 0 && x >= bytesPerPixel ? pixels[rowOffset + x - stride - bytesPerPixel] : 0;
      let value;

      if (filter === 0) {
        value = raw;
      } else if (filter === 1) {
        value = raw + left;
      } else if (filter === 2) {
        value = raw + above;
      } else if (filter === 3) {
        value = raw + Math.floor((left + above) / 2);
      } else if (filter === 4) {
        value = raw + paethPredictor(left, above, upperLeft);
      } else {
        throw new Error(`${fileName} uses unsupported PNG filter ${filter}`);
      }

      pixels[rowOffset + x] = value & 0xff;
    }

    sourceOffset += stride;
  }

  return pixels;
}

function paethPredictor(left, above, upperLeft) {
  const estimate = left + above - upperLeft;
  const distanceLeft = Math.abs(estimate - left);
  const distanceAbove = Math.abs(estimate - above);
  const distanceUpperLeft = Math.abs(estimate - upperLeft);

  if (distanceLeft <= distanceAbove && distanceLeft <= distanceUpperLeft) {
    return left;
  }
  if (distanceAbove <= distanceUpperLeft) {
    return above;
  }
  return upperLeft;
}

function assertTransparentCorners(data, fileName) {
  const { width, height, pixels } = parsePng(data, fileName);
  const cornerAlphaIndexes = [
    3,
    (width - 1) * 4 + 3,
    (height - 1) * width * 4 + 3,
    ((height - 1) * width + width - 1) * 4 + 3,
  ];

  if (cornerAlphaIndexes.some((alphaIndex) => pixels[alphaIndex] !== 0)) {
    throw new Error(`${fileName} must have fully transparent corner pixels`);
  }
}

function iconEntry(type, fileName) {
  const data = readFileSync(join(iconsetDirectory, fileName));
  assertTransparentCorners(data, fileName);

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
