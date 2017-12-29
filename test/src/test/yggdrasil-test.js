let request = require("../request");
let ursa = require("ursa");
let chai = require("chai");
let expect = chai.expect;
let YggdrasilVerifier = require("../yggdrasil-verify");
let config = require("../../yggdrasil-config");

let delay = (time) => (result) => new Promise(resolve => setTimeout(() => resolve(result), time));

let agent = {
	"name": "Minecraft",
	"version": 1
};

function exception(error) {
	return res => {
		let body = res.body;
		expect(body).to.be.an("object").that.has.all.keys("error", "errorMessage");
		expect(body.error).to.equal(error);
		expect(body.errorMessage).to.be.a("string");
	};
}

function namesOf(profiles) {
	let names = [];
	profiles.every(profile => names.push(profile.name));
	return names;
}

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

	describe("authenticate", function () {
		this.slow(config.rateLimits.login + 500);

		it("incorrect user",
			() => request.post("/authserver/authenticate")
				.send({
					username: "notExists@to2mbn.org",
					password: "123456",
					agent: agent
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException")));

		it("incorrect password",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user1.email,
					password: "incorrectPassword-_-",
					agent: agent
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException"))
				.then(delay(config.rateLimits.login)));

		it("rate limit",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user1.email,
					password: config.data.user1.password,
					agent: agent
				})
				.expect(200)
				.then(() => request.post("/authserver/authenticate")
					.send({
						username: config.data.user1.email,
						password: config.data.user1.password,
						agent: agent
					})
					.expect(403)
					.expect(exception("ForbiddenOperationException")))
				.then(delay(config.rateLimits.login)));

		it("user1 with requestUser=false",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user1.email,
					password: config.data.user1.password,
					agent: agent
				})
				.expect(200)
				.expect(res => {
					let response = res.body;
					verify.verifyAuthenticateResponse(response);
					expect(response.user).to.not.exist;
					expect(response.availableProfiles).to.be.empty;
				})
				.then(delay(config.rateLimits.login)));

		it("user1 with clientToken",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user1.email,
					password: config.data.user1.password,
					clientToken: "0d275e50-f5d1-4b7f-8d39-d66a3f904549X",
					agent: agent
				})
				.expect(200)
				.expect(res => {
					let response = res.body;
					verify.verifyAuthenticateResponse(response);
					expect(response.clientToken).to.equal("0d275e50-f5d1-4b7f-8d39-d66a3f904549X");
					expect(response.user).to.not.exist;
					expect(response.availableProfiles).to.be.empty;
				})
				.then(delay(config.rateLimits.login)));

		it("user1",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user1.email,
					password: config.data.user1.password,
					requestUser: true,
					agent: agent
				})
				.expect(200)
				.expect(res => {
					let response = res.body;
					verify.verifyAuthenticateResponse(response);
					expect(response.availableProfiles).to.be.empty;
					expect(response.selectedProfile).to.not.exist;
					expect(response.user).to.exist;
				})
				.then(delay(config.rateLimits.login)));

		it("user2",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user2.email,
					password: config.data.user2.password,
					requestUser: true,
					agent: agent
				})
				.expect(200)
				.expect(res => {
					let response = res.body;
					verify.verifyAuthenticateResponse(response);
					expect(namesOf(response.availableProfiles)).to.have.members([config.data.user2.character1.name]);
					expect(response.selectedProfile).to.exist;
					expect(response.selectedProfile.name).to.equal(config.data.user2.character1.name);
					expect(response.user).to.exist;
				})
				.then(delay(config.rateLimits.login)));

		it("user3",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user3.email,
					password: config.data.user3.password,
					requestUser: true,
					agent: agent
				})
				.expect(200)
				.expect(res => {
					let response = res.body;
					verify.verifyAuthenticateResponse(response);
					expect(namesOf(response.availableProfiles)).to.have.members([config.data.user3.character1.name, config.data.user3.character2.name]);
					expect(response.selectedProfile).to.not.exist;
					expect(response.user).to.exist;
				})
				.then(delay(config.rateLimits.login)));
	});
});
