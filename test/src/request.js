const request = require("supertest");

let apiRoot = process.env.API_ROOT || "http://localhost:8080";
console.info(`API root: ${apiRoot}`);
let baseRequest = request(apiRoot);

module.exports = baseRequest;
