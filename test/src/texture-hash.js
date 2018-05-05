const crypto = require("crypto");

function computeTextureHash(image) {
	const bufSize = 8192;
	let hash = crypto.createHash("sha256");
	let buf = Buffer.allocUnsafe(bufSize);
	let width = image.width;
	let height = image.height;
	buf.writeUInt32BE(width, 0);
	buf.writeUInt32BE(height, 4);
	let pos = 8;
	for (let x = 0; x < width; x++) {
		for (let y = 0; y < height; y++) {
			let imgidx = (width * y + x) << 2;
			let alpha = image.data[imgidx + 3];
			buf.writeUInt8(alpha, pos + 0);
			if (alpha === 0) {
				buf.writeUInt8(0, pos + 1);
				buf.writeUInt8(0, pos + 2);
				buf.writeUInt8(0, pos + 3);
			} else {
				buf.writeUInt8(image.data[imgidx + 0], pos + 1);
				buf.writeUInt8(image.data[imgidx + 1], pos + 2);
				buf.writeUInt8(image.data[imgidx + 2], pos + 3);
			}
			pos += 4;
			if (pos === bufSize) {
				pos = 0;
				hash.update(buf);
			}
		}
	}
	if (pos > 0) {
		hash.update(buf.slice(0, pos));
	}
	return hash.digest("hex");
}

module.exports = computeTextureHash;
