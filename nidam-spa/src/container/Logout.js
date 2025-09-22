import {useState} from "react";
import Button from "@mui/material/Button";
import axios from "axios";
import {useLocation} from "react-router-dom";

const Logout = props => {
	const location = useLocation();
	const [disabled, setDisabled] = useState(false);

	// There is no need to use Saga in this case.
	const logout = async () => {
		setDisabled(true);

		const currentPath = location.pathname + location.search + location.hash;
		console.log("currentPath: ", currentPath);

		const response = await axios.post(
			"/bff/logout",
			{},
			{
				headers: {
					"X-POST-LOGOUT-SUCCESS-URI": process.env.REACT_APP_BASE_URI + currentPath,
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