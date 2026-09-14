'use client';

import React from "react";
import Link from "@mui/material/Link";
import {usePathname, useSearchParams} from 'next/navigation';

import {CONFIG} from "../../config";

const Login = props => {
	const pathname = usePathname();
	const searchParams = useSearchParams();

	const login = (event) => {
		event.preventDefault();

		// const currentPath = "/";
		const currentPath = pathname + searchParams.toString() + window.location.hash;
		console.log("currentPath: ", currentPath);
		let url = new URL(CONFIG.LOGIN_URL);

		url.searchParams.append(
			"post_login_success_uri",
			`${CONFIG.BASE_URI}${currentPath}`
		)

		window.location.href = url.toString();
	}

	return <Link onClick={e => login(e)} style={{cursor: "pointer"}}>
		Already have an account? Sign in
	</Link>
}

export default Login;