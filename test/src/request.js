const request = require("supertest");

let apiRoot = process.env.npm_config_api_root || "http://localhost:8080";
console.info(`API root: ${apiRoot}`);
let baseRequest = request(apiRoot);

module.exports = baseRequest;
