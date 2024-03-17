import Link from "@mui/material/Link";
import React from "react";
import Button from "@mui/material/Button";


const Login = props => {

	const login = (event) => {
		event.preventDefault();

		const currentPath = "/";

		let url = new URL(process.env.REACT_APP_LOGIN_URL);

		url.searchParams.append(
			"post_login_success_uri",
			`${process.env.REACT_APP_BASE_URI}${currentPath}`
		)

		window.location.href = url.toString();
	}


	return <Button onClick={e => login(e)} variant="text" >
		Already have an account? Sign in
	</Button>
}

export default Login