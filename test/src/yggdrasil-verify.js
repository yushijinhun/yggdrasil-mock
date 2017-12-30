let ursa = require("ursa");
let chai = require("chai");
let expect = chai.expect;

chai.Assertion.addProperty("uuid", function () {
	this.assert(
		typeof this._obj === "string" && /^[0-9a-f]{8}[0-9a-f]{4}[1-5][0-9a-f]{3}[89ab][0-9a-f]{3}[0-9a-f]{12}$/i.test(this._obj),
		"expected #{this} to be a uuid",
		"expected #{this} to not be a uuid"
	);
});

function exists(x) {
	return x !== null && x !== undefined;
}

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

	verifyUser(user) {
		expect(user).to.be.an("object").that.has.all.keys("id", "properties");
		expect(user.id).to.be.a.uuid;
		this.verifyProperties(user.properties, false);
	}

	verifySimpleCharacter(character) {
		expect(character).to.be.an("object").that.has.all.keys("id", "name");
		expect(character.id).to.be.a.uuid;
		expect(character.name).to.be.a("string");
	}

	verifyAuthenticateResponse(response) {
		expect(response).to.be.an("object");
		expect(response.accessToken).to.be.a("string");
		expect(response.clientToken).to.be.a("string");
		expect(response.availableProfiles).to.be.an("array");
		response.availableProfiles.every(
			profile => this.verifySimpleCharacter(profile)
		);
		if (exists(response.selectedProfile)) {
			this.verifySimpleCharacter(response.selectedProfile);
			let existsInAvailableProfiles = false;
			for (let profile of response.availableProfiles) {
				if (profile.id === response.selectedProfile.id
					&& profile.name === response.selectedProfile.name) {
					existsInAvailableProfiles = true;
					break;
				}
			}
			expect(existsInAvailableProfiles).to.be.true;
		}
		if (exists(response.user)) {
			this.verifyUser(response.user);
		}
	}

	verifyRefreshResponse(response, lastResponse) {
		expect(response).to.be.an("object");
		expect(response.accessToken).to.be.a("string").that.not.equals(lastResponse.accessToken);
		expect(response.clientToken).to.be.a("string").that.equals(lastResponse.clientToken);
		if (exists(response.selectedProfile)) {
			this.verifySimpleCharacter(response.selectedProfile);
		}
		if (exists(response.user)) {
			this.verifyUser(response.user);
		}
	}
}

module.exports = YggdrasilVerifier;
