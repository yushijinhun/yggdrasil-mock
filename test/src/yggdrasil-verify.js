let ursa = require("ursa");
let chai = require("chai");
let expect = chai.expect;

class YggdrasilVerifier {
	constructor(meta) {
		expect(meta).to.be.an("object");
		expect(meta.signaturePublickey).to.be.a("string");
		expect(meta.skinDomains).to.be.an("array");
		this.publicKey = ursa.createPublicKey(Buffer(meta.signaturePublickey));
		this.skinDomains = meta.skinDomains;
	}

	verifyProperties(properties, shouldHaveSignatures) {
		expect(properties).to.be.an("array");
		let keys = new Set();
		properties.every(property => {
			expect(property).to.be.an("object");
			if (shouldHaveSignatures) {
				expect(property).to.have.all.keys("name", "value", "signature");
				expect(property.signature).to.be.a("string");
			} else {
				expect(property).to.have.all.keys("name", "value");
			}
			expect(property.name).to.be.a("string");
			expect(property.value).to.be.a("string");

			if (keys.has(property.name)) {
				throw `Duplicated property: ${property.name}`;
			}
			keys.add(property.name);

			if (shouldHaveSignatures) {
				this.verifySignature(property.value, property.signature);
			}
		});
	}

	verifySignature(value, signature) {
		if (!this.publicKey.hashAndVerify("sha1",
			Buffer(value, "utf-8"),
			Buffer(signature, "base64"))) {
			throw "Invalid signature";
		}
	}
}

module.exports = YggdrasilVerifier;
