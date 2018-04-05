let request = require("../request");
let ursa = require("ursa");
let chai = require("chai");
let expect = chai.expect;
let YggdrasilVerifier = require("../yggdrasil-verify");
let config = require("../../yggdrasil-config");
require("../texture-hash");

const slowTime = 300; // ms

const invalidAccessToken = "fa0e97770dec465aa3c5db8d70162857";
const invalidClientToken = "fa0e97770dec465aa3c5db8d70162857";
const nonexistentCharacterUUID = "992960dfc7a54afca041760004499434";
const nonexistentCharacterName = "characterNotExists";

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
				!it.includes(" ") && it !== "", "domain cannot be empty or contain whitespace")));
});

describe("yggdrasil basic api", function () {
	let verify;
	before(done => {
		request.get("/")
			.expect(200)
			.expect(res => verify = new YggdrasilVerifier(res.body))
			.end(done);
	});

	function authenticateUser1() {
		return request.post("/authserver/authenticate")
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
			.then(res => res.body)
			.then(delay(config.rateLimits.login));
	}

	function authenticateUser2() {
		return request.post("/authserver/authenticate")
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
			.then(res => res.body)
			.then(delay(config.rateLimits.login));
	}

	function authenticateUser3() {
		return request.post("/authserver/authenticate")
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
			.then(res => res.body)
			.then(delay(config.rateLimits.login));
	}

	function signoutUser1() {
		return request.post("/authserver/signout")
			.send({
				username: config.data.user1.email,
				password: config.data.user1.password
			})
			.expect(204)
			.then(delay(config.rateLimits.login));
	}

	function tokenShouldBeInvalid(accessToken) {
		return request.post("/authserver/validate")
			.send({
				accessToken: accessToken
			})
			.expect(403)
			.expect(exception("ForbiddenOperationException"));
	}

	function tokenShouldBeValid(accessToken) {
		return request.post("/authserver/validate")
			.send({
				accessToken: accessToken
			})
			.expect(204);
	}

	function tokenShouldBeUnrefreshable(accessToken) {
		return request.post("/authserver/refresh")
			.send({
				accessToken: accessToken
			})
			.expect(403)
			.expect(exception("ForbiddenOperationException"));
	}

	describe("authenticate", function () {

		this.slow(slowTime);
		it("incorrect user",
			() => request.post("/authserver/authenticate")
				.send({
					username: "notExists@to2mbn.org",
					password: "123456",
					agent: agent
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException")));

		this.slow(slowTime + config.rateLimits.login);
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

		this.slow(slowTime + config.rateLimits.login);
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

		this.slow(slowTime + config.rateLimits.login);
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

		this.slow(slowTime + config.rateLimits.login);
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

		this.slow(slowTime + config.rateLimits.login);
		it("user1", () => authenticateUser1());

		this.slow(slowTime + config.rateLimits.login);
		it("user2", () => authenticateUser2());

		this.slow(slowTime + config.rateLimits.login);
		it("user3", () => authenticateUser3());
	});

	describe("refresh", function () {
		function verifyUser1or3RefreshResponse(response, authResponse) {
			verify.verifyRefreshResponse(response, authResponse);
			expect(response.selectedProfile).to.not.exist;
			expect(response.user).to.not.exist;
		}

		function findProfile(name, availableProfiles) {
			let characterX;
			for (let character of availableProfiles) {
				if (character.name === name) {
					characterX = character;
					break;
				}
			}
			expect(characterX).to.exist;
			return characterX;
		}

		function selectProfile(name, availableProfiles, lastResponse) {
			let characterX = findProfile(name, availableProfiles);
			return request.post("/authserver/refresh")
				.send({
					accessToken: lastResponse.accessToken,
					selectedProfile: characterX
				})
				.expect(200)
				.expect(res => {
					let response = res.body;
					verify.verifyRefreshResponse(response, lastResponse);
					expect(response.user).to.not.exist;
					expect(response.selectedProfile).to.exist;
					expect(response.selectedProfile.id).to.equal(characterX.id);
					expect(response.selectedProfile.name).to.equal(name);
				})
				.then(res => res.body);
		}

		this.slow(slowTime);
		it("incorrect accessToken",
			() => request.post("/authserver/refresh")
				.send({
					accessToken: invalidAccessToken
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException")));

		this.slow(slowTime + config.rateLimits.login);
		it("incorrect clientToken",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken,
						clientToken: invalidClientToken
					})
					.expect(403)
					.expect(exception("ForbiddenOperationException"))));

		this.slow(slowTime + config.rateLimits.login);
		it("user1 with clientToken",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken,
						clientToken: authResponse.clientToken
					})
					.expect(200)
					.expect(res => verifyUser1or3RefreshResponse(res.body, authResponse))));

		this.slow(slowTime + config.rateLimits.login);
		it("user1 with requestUser=true",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken,
						requestUser: true
					})
					.expect(200)
					.expect(res => {
						let response = res.body;
						verify.verifyRefreshResponse(response, authResponse);
						expect(response.selectedProfile).to.not.exist;
						expect(response.user).to.exist;
					})));

		this.slow(slowTime + config.rateLimits.login);
		it("old token should be un-refreshable",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken
					})
					.expect(200)
					.expect(res => verifyUser1or3RefreshResponse(res.body, authResponse))
					.then(() => request.post("/authserver/refresh")
						.send({
							accessToken: authResponse.accessToken
						})
						.expect(403)
						.expect(exception("ForbiddenOperationException")))));

		this.slow(slowTime + config.rateLimits.login);
		it("continuous refresh",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken
					})
					.expect(200)
					.expect(res => verifyUser1or3RefreshResponse(res.body, authResponse))
					.then(res => res.body))
				.then(lastResponse => request.post("/authserver/refresh")
					.send({
						accessToken: lastResponse.accessToken
					})
					.expect(200)
					.expect(res => verifyUser1or3RefreshResponse(res.body, lastResponse))));

		this.slow(slowTime + config.rateLimits.login);
		it("user2",
			() => authenticateUser2()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken
					})
					.expect(200)
					.expect(res => {
						let response = res.body;
						verify.verifyRefreshResponse(response, authResponse);
						expect(response.user).not.exist;
						expect(response.selectedProfile).to.exist;
						expect(response.selectedProfile.id).to.equal(authResponse.selectedProfile.id);
						expect(response.selectedProfile.name).to.equal(config.data.user2.character1.name);
					})));

		this.slow(slowTime + config.rateLimits.login);
		it("user3",
			() => authenticateUser3()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken
					})
					.expect(200)
					.expect(res => {
						let response = res.body;
						verify.verifyRefreshResponse(response, authResponse);
						expect(response.user).not.exist;
						expect(response.selectedProfile).to.not.exist;
					})));

		this.slow(slowTime + config.rateLimits.login);
		it("select profile",
			() => authenticateUser3()
				.then(authResponse => selectProfile(config.data.user3.character2.name, authResponse.availableProfiles, authResponse)));

		this.slow(slowTime + config.rateLimits.login);
		it("select nonexistent profile (expecting old token to be still valid)",
			() => authenticateUser3()
				.then(authResponse => request.post("/authserver/refresh")
					.send({
						accessToken: authResponse.accessToken,
						selectedProfile: {
							id: nonexistentCharacterUUID,
							name: nonexistentCharacterName
						}
					})
					.expect(400)
					.expect(exception("IllegalArgumentException"))
					.then(() => request.post("/authserver/refresh")
						.send({
							accessToken: authResponse.accessToken
						})
						.expect(200)
						.expect(res => verifyUser1or3RefreshResponse(res.body, authResponse)))));

		this.slow(slowTime + 2 * config.rateLimits.login);
		it("select another user's profile (expecting old token to be still valid)",
			() => authenticateUser2()
				.then(res => res.selectedProfile)
				.then(othersProfile => authenticateUser3()
					.then(authResponse => request.post("/authserver/refresh")
						.send({
							accessToken: authResponse.accessToken,
							selectedProfile: othersProfile
						})
						.expect(403)
						.expect(exception("ForbiddenOperationException"))
						.then(() => request.post("/authserver/refresh")
							.send({
								accessToken: authResponse.accessToken
							})
							.expect(200)
							.expect(res => verifyUser1or3RefreshResponse(res.body, authResponse))))));

		this.slow(slowTime + config.rateLimits.login);
		it("select profile with an already-bound token (expecting to fail)",
			() => authenticateUser3()
				.then(authResponse => selectProfile(config.data.user3.character2.name, authResponse.availableProfiles, authResponse)
					.then(lastResponse => request.post("/authserver/refresh")
						.send({
							accessToken: lastResponse.accessToken,
							selectedProfile: findProfile(config.data.user3.character1.name, authResponse.availableProfiles)
						})
						.expect(400)
						.expect(exception("IllegalArgumentException")))));
	});

	describe("validate", function () {

		this.slow(slowTime);
		it("incorrect accessToken",
			() => request.post("/authserver/validate")
				.send({
					accessToken: invalidAccessToken
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException")));

		this.slow(slowTime + config.rateLimits.login);
		it("incorrect clientToken",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/validate")
					.send({
						accessToken: authResponse.accessToken,
						clientToken: invalidClientToken
					})
					.expect(403)
					.expect(exception("ForbiddenOperationException"))));

		this.slow(slowTime + config.rateLimits.login);
		it("user1 with clientToken",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/validate")
					.send({
						accessToken: authResponse.accessToken,
						clientToken: authResponse.clientToken
					})
					.expect(204)));

		this.slow(slowTime + config.rateLimits.login);
		it("user1",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/validate")
					.send({
						accessToken: authResponse.accessToken
					})
					.expect(204)));

	});

	describe("invalidate", function () {
		this.slow(slowTime);
		it("incorrect accessToken",
			() => request.post("/authserver/invalidate")
				.send({
					accessToken: invalidAccessToken
				})
				.expect(204));

		this.slow(slowTime + config.rateLimits.login);
		it("incorrect clientToken",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/invalidate")
					.send({
						accessToken: authResponse.accessToken,
						clientToken: invalidClientToken
					})
					.expect(204)
					.then(() => authResponse))
				.then(lastResponse => tokenShouldBeInvalid(lastResponse.accessToken)));

		this.slow(slowTime + config.rateLimits.login);
		it("user1 with clientToken (expecting token to be invalid)",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/invalidate")
					.send({
						accessToken: authResponse.accessToken,
						clientToken: authResponse.clientToken
					})
					.expect(204)
					.then(() => authResponse))
				.then(lastResponse => tokenShouldBeInvalid(lastResponse.accessToken)));

		this.slow(slowTime + config.rateLimits.login);
		it("user1 (expecting token to be invalid)",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/invalidate")
					.send({
						accessToken: authResponse.accessToken
					})
					.expect(204)
					.then(() => authResponse))
				.then(lastResponse => tokenShouldBeInvalid(lastResponse.accessToken)));

		this.slow(slowTime + config.rateLimits.login);
		it("user1 (expecting token to be un-refreshable)",
			() => authenticateUser1()
				.then(authResponse => request.post("/authserver/invalidate")
					.send({
						accessToken: authResponse.accessToken
					})
					.expect(204)
					.then(() => authResponse))
				.then(lastResponse => tokenShouldBeUnrefreshable(lastResponse.accessToken)));

		this.slow(slowTime + 2 * config.rateLimits.login);
		it("should not affect other tokens (expecting revoked token to be un-refreshable)",
			() => authenticateUser1()
				.then(authResponse1 => authenticateUser1()
					.then(authResponse2 => [authResponse1, authResponse2]))
				.then(authResponses => request.post("/authserver/invalidate")
					.send({
						accessToken: authResponses[0].accessToken
					})
					.expect(204)
					.then(() => authResponses))
				.then(authResponses => tokenShouldBeUnrefreshable(authResponses[0].accessToken)
					.then(() => authResponses))
				.then(authResponses => tokenShouldBeValid(authResponses[1].accessToken)));

	});

	describe("signout", function () {

		this.slow(slowTime);
		it("incorrect user",
			() => request.post("/authserver/signout")
				.send({
					username: "notExists@to2mbn.org",
					password: "123456"
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException")));

		this.slow(slowTime + config.rateLimits.login);
		it("incorrect password",
			() => request.post("/authserver/signout")
				.send({
					username: config.data.user1.email,
					password: "incorrectPassword-_-"
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException"))
				.then(delay(config.rateLimits.login)));

		this.slow(slowTime + 2 * config.rateLimits.login);
		it("user1 (expecting token to be invalid)",
			() => authenticateUser1()
				.then(authResponse => signoutUser1()
					.then(() => authResponse))
				.then(lastResponse => tokenShouldBeInvalid(lastResponse.accessToken)));

		this.slow(slowTime + 2 * config.rateLimits.login);
		it("user1 (expecting token to be un-refreshable)",
			() => authenticateUser1()
				.then(authResponse => signoutUser1()
					.then(() => authResponse))
				.then(lastResponse => tokenShouldBeUnrefreshable(lastResponse.accessToken)));

		this.slow(slowTime + 3 * config.rateLimits.login);
		it("should revoke all tokens (expecting tokens to be un-refreshable)",
			() => authenticateUser1()
				.then(authResponse1 => authenticateUser1()
					.then(authResponse2 => [authResponse1, authResponse2]))
				.then(authResponses => signoutUser1()
					.then(() => authResponses))
				.then(authResponses => tokenShouldBeUnrefreshable(authResponses[0].accessToken)
					.then(() => authResponses))
				.then(authResponses => tokenShouldBeUnrefreshable(authResponses[1].accessToken)));

		this.slow(slowTime + config.rateLimits.login);
		it("rate limit",
			() => request.post("/authserver/signout")
				.send({
					username: config.data.user1.email,
					password: config.data.user1.password
				})
				.expect(204)
				.then(() => request.post("/authserver/signout")
					.send({
						username: config.data.user1.email,
						password: config.data.user1.password
					})
					.expect(403)
					.expect(exception("ForbiddenOperationException")))
				.then(delay(config.rateLimits.login)));
	});

	describe("query character names", function () {
		this.slow(slowTime);

		it("empty payload",
			() => request.post("/api/profiles/minecraft")
				.send([])
				.expect(200)
				.expect(res =>
					expect(res.body).to.be.an("array").that.is.empty));

		it("a nonexistent character",
			() => request.post("/api/profiles/minecraft")
				.send([nonexistentCharacterName])
				.expect(200)
				.expect(res =>
					expect(res.body).to.be.an("array").that.is.empty));

		it("character1",
			() => request.post("/api/profiles/minecraft")
				.send([config.data.user2.character1.name])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.key(config.data.user2.character1.name);
				}));

		it("a nonexistent character and character1",
			() => request.post("/api/profiles/minecraft")
				.send(["characterNotExists", config.data.user2.character1.name])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.key(config.data.user2.character1.name);
				}));

		it("duplicated character1",
			() => request.post("/api/profiles/minecraft")
				.send([config.data.user2.character1.name, config.data.user2.character1.name])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.key(config.data.user2.character1.name);
				}));

		it("character1 and character2",
			() => request.post("/api/profiles/minecraft")
				.send([config.data.user2.character1.name, config.data.user3.character1.name])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.all.keys([config.data.user2.character1.name, config.data.user3.character1.name]);
				}));
	});

	describe("query profiles", function () {
		this.slow(slowTime);

		let character1 = config.data.user2.character1.name;
		let character2 = config.data.user3.character1.name;
		let character3 = config.data.user3.character2.name;
		let uuids;
		before(done => {
			request.post("/api/profiles/minecraft")
				.send([character1, character2, character3])
				.expect(200)
				.expect(res => {
					uuids = verify.verifyNameQueryResponse(res.body);
					expect(uuids).to.have.all.keys([character1, character2, character3]);
				})
				.end(done);
		});

		function queryCharacter(characterName, withSignature = false, urlQuery = "") {
			return request.get("/sessionserver/session/minecraft/profile/" + uuids.get(characterName) + urlQuery)
				.expect(200)
				.expect(res => {
					let response = res.body;
					verify.verifyCompleteCharacter(response, withSignature);
					expect(response.id).to.equal(uuids.get(characterName));
					expect(response.name).to.equal(characterName);
				})
				.then(res => res.body);
		}

		it("a nonexistent character",
			() => request.get("/sessionserver/session/minecraft/profile/992960dfc7a54afca041760004499434")
				.expect(204));

		it("character1 with unsigned=true",
			() => queryCharacter(character1, false, "?unsigned=true")
				.then(verify.extractAndVerifyTexturesPayload)
				.then(it => {
					expect(it.skin).to.not.be.null;
					expect(it.cape).to.not.be.null;
					expect(it.slim).to.false;
				}));

		it("character1 with unsigned=false",
			() => queryCharacter(character1, true, "?unsigned=false")
				.then(verify.extractAndVerifyTexturesPayload)
				.then(it => {
					expect(it.skin).to.not.be.null;
					expect(it.cape).to.not.be.null;
					expect(it.slim).to.false;
				}));

		it("character1",
			() => queryCharacter(character1)
				.then(verify.extractAndVerifyTexturesPayload)
				.then(it => {
					expect(it.skin).to.not.be.null;
					expect(it.cape).to.not.be.null;
					expect(it.slim).to.false;
				}));

		it("character2",
			() => queryCharacter(character2)
				.then(verify.extractAndVerifyTexturesPayload)
				.then(it => {
					expect(it.skin).to.not.be.null;
					expect(it.cape).to.be.null;
					expect(it.slim).to.true;
				}));

		it("character3",
			() => queryCharacter(character3)
				.then(verify.extractAndVerifyTexturesPayload)
				.then(it => {
					expect(it.skin).to.be.null;
					expect(it.cape).to.not.be.null;
					expect(it.slim).to.be.null;
				}));

	});

	// describe("textures");

	// describe("session");

});
