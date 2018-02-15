module.exports = {
	"env": {
		"es6": true,
		"node": true,
		"mocha": true
	},
	"plugins": [
		"mocha"
	],
	"extends": "eslint:recommended",
	"parserOptions": {
		"sourceType": "module"
	},
	"rules": {
		"indent": [
			"error",
			"tab",
			{
				"SwitchCase": 1
			}
		],
		"linebreak-style": [
			"error",
			"unix"
		],
		"quotes": [
			"error",
			"double"
		],
		"semi": [
			"error",
			"always"
		],
		"no-console": [
			"error",
			{
				allow: ["info", "warn", "error"]
			}
		]
	}
};
