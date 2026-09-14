'use client';

import {useState} from "react";
import Button from "@mui/material/Button";
import axios from "axios";
import {usePathname, useSearchParams} from 'next/navigation';

import { CONFIG } from "../../config";

const Logout = props => {
	const pathname = usePathname();
	const searchParams = useSearchParams();
	const [disabled, setDisabled] = useState(false);

	// There is no need to use Saga in this case.
	const logout = async () => {
		setDisabled(true);

		const currentPath = pathname + searchParams.toString() + window.location.hash;
		console.log("currentPath: ", currentPath);

		const response = await axios.post(
			CONFIG.LOGOUT_URL,
			{},
			{
				headers: {
					"X-POST-LOGOUT-SUCCESS-URI": CONFIG.BASE_URI + currentPath,
				},
			}
		);
		// console.log("logout response: ", JSON.stringify(response.headers["location"]));
		window.location.href = response.headers["location"];
		setDisabled(false);
	};

	return <Button variant="contained" disabled={disabled} onClick={logout}>Logout</Button>
}

export default Logout;