module.exports = {
	apiRoot: "http://localhost:8080",

	rateLimits: {
		// 单位：ms
		login: 500
	},

	data: {
		// 不能存在的用户：
		// notExists@to2mbn.org

		// 不能存在的角色：
		// 992960dfc7a54afca041760004499434 - characterNotExists

		user1: {
			// 此用户不拥有角色
			email: "test1@to2mbn.org",
			// 不能使用的密码：
			// incorrectPassword-_-
			password: "111111"
		},

		user2: {
			// 此用户拥有1个角色
			email: "test2@to2mbn.org",
			password: "222222",
			character1: {
				// 角色须为Steve
				name: "character1"
			}
		},

		user3: {
			// 此用户拥有2个角色
			email: "test3@to2mbn.org",
			password: "333333",
			character1: {
				// 角色须为Steve
				name: "character2"
			},
			character2: {
				// 角色须为Alex
				name: "character3"
			}
		}
	}

};
