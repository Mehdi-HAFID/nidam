import Button from "@mui/material/Button";
import axios from "axios";
import {useState} from "react";

const Logout = props => {

	const [disabled, setDisabled] = useState(false);

	// There is no need to use Saga in this case.
	const logout = async () => {
		setDisabled(true);
		const response = await axios.post(
			"/bff/logout",
			{},
			{
				headers: {
					"X-POST-LOGOUT-SUCCESS-URI": process.env.REACT_APP_BASE_URI,
				},
			}
		);
		// console.log("logout response: ", JSON.stringify(response));
		window.location.href = response.headers["location"];
		setDisabled(false);
	};

	return <Button variant="contained" sx={{width: "25%"}} disabled={disabled} onClick={logout}>Logout</Button>
}

export default Logout;