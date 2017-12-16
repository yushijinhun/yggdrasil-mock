let request = require("../request");
let ursa = require("ursa");
let chai = require("chai");
let expect = chai.expect;
let YggdrasilVerifier = require("../yggdrasil-verify");

describe("yggdrasil extension api", function () {
	let response;
	before(done => {
		request.get("/")
			.expect(200)
			.expect(res => {
				response = res.body;
				expect(response).to.be.an("object").that.has.all.keys("meta", "skinDomains", "signaturePublickey");
				expect(response.meta).to.be.an("object");
				expect(response.skinDomains).to.be.an("array");
				expect(response.signaturePublickey).to.be.a("string");
			})
			.end(done);
	});

	describe("should have valid public key", function () {
		let publicKey;
		before(() => publicKey = ursa.createPublicKey(Buffer(response.signaturePublickey)));
		it("should be at least 4096 bits long", () =>
			expect(publicKey.getModulus().length >= 512));
	});

	it("should have specified metadata", () =>
		expect(response.meta).to.include.keys("serverName", "implementationName", "implementationVersion"));

	it("should have valid skin domains", () =>
		response.skinDomains.every(domain =>
			expect(domain).to.be.a("string").that.satisfies(it =>
				it.startsWith("."), "domain should start with a dot")));
});

describe("yggdrasil basic api", function () {
	let verify;
	before(done => {
		request.get("/")
			.expect(200)
			.expect(res => verify = new YggdrasilVerifier(res.body))
			.end(done);
	});
	// TODO
	it("// TODO");
});
