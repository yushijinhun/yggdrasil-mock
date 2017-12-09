let config = require("../yggdrasil-config");
let request = require("supertest");

let baseRequest = request(config.apiRoot);

module.exports = baseRequest;
