let supertest = require("supertest")("");
let PNG = require("pngjs-nozlib").PNG;
let streams = require("memory-streams");
let request = require("../request");
let ursa = require("ursa");
let chai = require("chai");
let expect = chai.expect;
let YggdrasilVerifier = require("../yggdrasil-verify");
let config = require("../../yggdrasil-config");
let crypto = require("crypto");
let computeTextureHash = require("../texture-hash");
const { URL } = require("url");

const slowTime = 300; // ms

const invalidAccessToken = "fa0e97770dec465aa3c5db8d70162857";
const invalidClientToken = "fa0e97770dec465aa3c5db8d70162857";
const nonexistentCharacterUUID = "992960dfc7a54afca041760004499434";
const nonexistentCharacterName = "characterNotExists";
const nonexistentUser = "notExists@to2mbn.org";
const nonexistentUserPassword = "123456";
const incorrectPassword = "incorrectPassword-_-";

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

	let u2character1 = config.data.user2.character1.name;
	let u3character1 = config.data.user3.character1.name;
	let u3character2 = config.data.user3.character2.name;

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
				expect(namesOf(response.availableProfiles)).to.have.members([u2character1]);
				expect(response.selectedProfile).to.exist;
				expect(response.selectedProfile.name).to.equal(u2character1);
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
				expect(namesOf(response.availableProfiles)).to.have.members([u3character1, u3character2]);
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
					username: nonexistentUser,
					password: nonexistentUserPassword,
					agent: agent
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException")));

		this.slow(slowTime + config.rateLimits.login);
		it("incorrect password",
			() => request.post("/authserver/authenticate")
				.send({
					username: config.data.user1.email,
					password: incorrectPassword,
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

	describe("refresh", function () {
		function verifyUser1or3RefreshResponse(response, authResponse) {
			verify.verifyRefreshResponse(response, authResponse);
			expect(response.selectedProfile).to.not.exist;
			expect(response.user).to.not.exist;
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
						expect(response.selectedProfile.name).to.equal(u2character1);
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
				.then(authResponse => selectProfile(u3character2, authResponse.availableProfiles, authResponse)));

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
				.then(authResponse => selectProfile(u3character2, authResponse.availableProfiles, authResponse)
					.then(lastResponse => request.post("/authserver/refresh")
						.send({
							accessToken: lastResponse.accessToken,
							selectedProfile: findProfile(u3character1, authResponse.availableProfiles)
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
					username: nonexistentUser,
					password: nonexistentUserPassword
				})
				.expect(403)
				.expect(exception("ForbiddenOperationException")));

		this.slow(slowTime + config.rateLimits.login);
		it("incorrect password",
			() => request.post("/authserver/signout")
				.send({
					username: config.data.user1.email,
					password: incorrectPassword
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

		it(`${u2character1}`,
			() => request.post("/api/profiles/minecraft")
				.send([u2character1])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.key(u2character1);
				}));

		it(`a nonexistent character and ${u2character1}`,
			() => request.post("/api/profiles/minecraft")
				.send(["characterNotExists", u2character1])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.key(u2character1);
				}));

		it(`duplicated ${u2character1}`,
			() => request.post("/api/profiles/minecraft")
				.send([u2character1, u2character1])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.key(u2character1);
				}));

		it(`${u2character1} and ${u3character1}`,
			() => request.post("/api/profiles/minecraft")
				.send([u2character1, u3character1])
				.expect(200)
				.expect(res => {
					let result = verify.verifyNameQueryResponse(res.body);
					expect(result).to.have.all.keys([u2character1, u3character1]);
				}));
	});

	describe("session", function () {
		let uuids;
		before(done => {
			request.post("/api/profiles/minecraft")
				.send([u2character1, u3character1, u3character2])
				.expect(200)
				.expect(res => {
					uuids = verify.verifyNameQueryResponse(res.body);
					expect(uuids).to.have.all.keys([u2character1, u3character1, u3character2]);
				})
				.end(done);
		});

		function verifyU2character1(character, withSignature) {
			verify.verifyCompleteCharacter(character, withSignature);
			expect(character.name).to.equal(u2character1);
			let textures = verify.extractAndVerifyTexturesPayload(character);
			expect(textures.skin).to.not.be.null;
			expect(textures.cape).to.not.be.null;
			expect(textures.slim).to.false;
			return textures;
		}

		function verifyU3character1(character, withSignature) {
			verify.verifyCompleteCharacter(character, withSignature);
			expect(character.name).to.equal(u3character1);
			let textures = verify.extractAndVerifyTexturesPayload(character);
			expect(textures.skin).to.not.be.null;
			expect(textures.cape).to.be.null;
			expect(textures.slim).to.true;
			return textures;
		}

		function verifyU3character2(character, withSignature) {
			verify.verifyCompleteCharacter(character, withSignature);
			expect(character.name).to.equal(u3character2);
			let textures = verify.extractAndVerifyTexturesPayload(character);
			expect(textures.skin).to.be.null;
			expect(textures.cape).to.not.be.null;
			expect(textures.slim).to.null;
			return textures;
		}

		describe("query profiles", function () {
			this.slow(slowTime);

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

			it(`${u2character1} with unsigned=true`,
				() => queryCharacter(u2character1, false, "?unsigned=true")
					.then(it => verifyU2character1(it, false)));

			it(`${u2character1} with unsigned=false`,
				() => queryCharacter(u2character1, true, "?unsigned=false")
					.then(it => verifyU2character1(it, true)));

			it(`${u2character1}`,
				() => queryCharacter(u2character1)
					.then(it => verifyU2character1(it, false)));

			it(`${u3character1}`,
				() => queryCharacter(u3character1)
					.then(it => verifyU3character1(it, false)));

			it(`${u3character2}`,
				() => queryCharacter(u3character2)
					.then(it => verifyU3character2(it, false)));

		});

		function randomServerId() {
			return crypto.randomBytes(16).toString("hex");
		}

		function joinU2character1() {
			return authenticateUser2()
				.then(res => {
					let serverid = randomServerId();
					return request.post("/sessionserver/session/minecraft/join")
						.send({
							accessToken: res.accessToken,
							selectedProfile: uuids.get(u2character1),
							serverId: serverid
						})
						.expect(204)
						.then(() => serverid);
				});
		}

		function joinU3characterX(character) {
			return authenticateUser3()
				.then(authResponse => selectProfile(character, authResponse.availableProfiles, authResponse))
				.then(res => {
					let serverid = randomServerId();
					return request.post("/sessionserver/session/minecraft/join")
						.send({
							accessToken: res.accessToken,
							selectedProfile: uuids.get(character),
							serverId: serverid
						})
						.expect(204)
						.then(() => serverid);
				});
		}

		function joinU3character1() {
			return joinU3characterX(u3character1);
		}

		function joinU3character2() {
			return joinU3characterX(u3character2);
		}

		describe("join server", function () {

			this.slow(slowTime);
			it("incorrect accessToken",
				() => {
					let serverid = randomServerId();
					return request.post("/sessionserver/session/minecraft/join")
						.send({
							accessToken: invalidAccessToken,
							selectedProfile: uuids.get(u2character1),
							serverId: serverid
						})
						.expect(403)
						.expect(exception("ForbiddenOperationException"));
				});

			function testJoinFailure(authFunc, characterUUID) {
				return authFunc()
					.then(auth => {
						let serverid = randomServerId();
						return request.post("/sessionserver/session/minecraft/join")
							.send({
								accessToken: auth.accessToken,
								selectedProfile: characterUUID,
								serverId: serverid
							})
							.expect(403)
							.expect(exception("ForbiddenOperationException"));
					});
			}

			function testNonexistentCharacterWithUserX(authFunc) {
				return testJoinFailure(authFunc, nonexistentCharacterUUID);
			}

			this.slow(slowTime + config.rateLimits.login);
			it("nonexistent character with user1",
				() => testNonexistentCharacterWithUserX(authenticateUser1));

			this.slow(slowTime + config.rateLimits.login);
			it("nonexistent character with user2",
				() => testNonexistentCharacterWithUserX(authenticateUser1));

			this.slow(slowTime + config.rateLimits.login);
			it("nonexistent character with user3",
				() => testNonexistentCharacterWithUserX(authenticateUser1));

			this.slow(slowTime + config.rateLimits.login);
			it("another character that belongs to current user (user3 with no selection)",
				() => testJoinFailure(authenticateUser3, uuids.get(u3character1)));

			this.slow(slowTime + config.rateLimits.login);
			it("another character that belongs to current user (user3 with selection)",
				() => authenticateUser3()
					.then(authResponse => selectProfile(u3character1, authResponse.availableProfiles, authResponse))
					.then(res => {
						let serverid = randomServerId();
						return request.post("/sessionserver/session/minecraft/join")
							.send({
								accessToken: res.accessToken,
								selectedProfile: uuids.get(u3character2),
								serverId: serverid
							})
							.expect(403)
							.expect(exception("ForbiddenOperationException"));
					}));

			this.slow(slowTime + config.rateLimits.login);
			it(`another user's character (${u2character1} with user1)`,
				() => testJoinFailure(authenticateUser1, uuids.get(u2character1)));

			this.slow(slowTime + config.rateLimits.login);
			it(`another user's character (${u3character1} with user2)`,
				() => testJoinFailure(authenticateUser2, uuids.get(u3character1)));

			this.slow(slowTime + config.rateLimits.login);
			it(`another user's character (${u2character1} with user3)`,
				() => testJoinFailure(authenticateUser3, uuids.get(u2character1)));

			this.slow(slowTime + config.rateLimits.login);
			it(`${u2character1}`,
				() => joinU2character1());

			this.slow(slowTime + config.rateLimits.login);
			it(`${u3character1}`,
				() => joinU3character1());

			this.slow(slowTime + config.rateLimits.login);
			it(`${u3character2}`,
				() => joinU3character2());
		});

		describe("has joined server", function () {

			this.slow(slowTime + config.rateLimits.login);
			it("incorrect username",
				() => joinU2character1()
					.then(serverid => request.get(`/sessionserver/session/minecraft/hasJoined?username=${u3character1}&serverId=${serverid}`)
						.expect(204)));

			this.slow(slowTime + config.rateLimits.login);
			it("incorrect serverid",
				() => joinU2character1()
					.then(() => request.get(`/sessionserver/session/minecraft/hasJoined?username=${u2character1}&serverId=${randomServerId()}`)
						.expect(204)));

			this.slow(slowTime);
			it("no leading join request",
				() => request.get(`/sessionserver/session/minecraft/hasJoined?username=${u2character1}&serverId=${randomServerId()}`)
					.expect(204));

			this.slow(slowTime + config.rateLimits.login);
			it(`${u2character1}`,
				() => joinU2character1()
					.then(serverid => request.get(`/sessionserver/session/minecraft/hasJoined?username=${u2character1}&serverId=${serverid}`)
						.expect(200)
						.expect(res => verifyU2character1(res.body, true))));

			this.slow(slowTime + config.rateLimits.login);
			it(`${u3character1}`,
				() => joinU3character1()
					.then(serverid => request.get(`/sessionserver/session/minecraft/hasJoined?username=${u3character1}&serverId=${serverid}`)
						.expect(200)
						.expect(res => verifyU3character1(res.body, true))));

			this.slow(slowTime + config.rateLimits.login);
			it(`${u3character2}`,
				() => joinU3character2()
					.then(serverid => request.get(`/sessionserver/session/minecraft/hasJoined?username=${u3character2}&serverId=${serverid}`)
						.expect(200)
						.expect(res => verifyU3character2(res.body, true))));
		});

		describe("textures", function () {
			let u2character1Textures;
			let u3character1Textures;
			let u3character2Textures;
			let domains;

			before(done => {
				function query(character, callback) {
					return request.get("/sessionserver/session/minecraft/profile/" + uuids.get(character))
						.expect(200)
						.then(res => {
							callback(res.body);
						});
				}
				function getDomainWhitelist(callback) {
					return request.get("/")
						.expect(200)
						.then(res => callback(res.body.skinDomains));
				}
				getDomainWhitelist(it => domains = it)
					.then(() => query(u2character1, it => u2character1Textures = verifyU2character1(it)))
					.then(() => query(u3character1, it => u3character1Textures = verifyU3character1(it)))
					.then(() => query(u3character2, it => u3character2Textures = verifyU3character2(it)))
					.then(done);
			});

			function downloadAsImage(urlString) {
				let url = new URL(urlString);
				let inWhitelist = false;
				for (let domain of domains) {
					if (url.hostname.endsWith(domain)) {
						inWhitelist = true;
						break;
					}
				}
				expect(inWhitelist, `domain ${url.hostname} is not in whitelist`).to.be.true;

				let stream = new streams.WritableStream();

				return new Promise(
					resolve => supertest.get(url)
						.expect(200)
						.pipe(stream)
						.on("finish", resolve))
					.then(() => {
						let image = PNG.sync.read(stream.toBuffer());
						let hash = computeTextureHash(image);

						expect(
							url.pathname.endsWith(hash) ||
							url.pathname.endsWith(hash + ".png"),
							`texture url(${url}) should end with its hash(${hash})`
						).to.be.true;
						return image;
					});
			}

			function verifySkin(image) {
				expect(
					image.width == image.height ||
					image.width == 2 * image.height,
					`invalid skin size ${image.width}x${image.height}`
				).to.be.true;
			}

			function verifyCape(image) {
				expect(
					image.width == 2 * image.height,
					`invalid cape size ${image.width}x${image.height}`
				).to.be.true;
			}

			it(`skin of ${u2character1} (steve)`,
				() => downloadAsImage(u2character1Textures.skin)
					.then(verifySkin));

			it(`cape of ${u2character1}`,
				() => downloadAsImage(u2character1Textures.cape)
					.then(verifyCape));

			it(`skin of ${u3character1} (alex)`,
				() => downloadAsImage(u3character1Textures.skin)
					.then(verifySkin));

			it(`cape of ${u3character2}`,
				() => downloadAsImage(u3character2Textures.cape)
					.then(verifyCape));
		});

	});

});
