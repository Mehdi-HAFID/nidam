import {useEffect} from "react";
import * as registerSagas from "../redux/register/saga";
import {useDispatch, useSelector} from "react-redux";

const Private = props => {
	const secret = useSelector((state) => state.register.secret);
	const dispatch = useDispatch();

	useEffect(() => {
		dispatch(registerSagas.GetMySecret());
	}, []);


	console.log("secret", secret);
	return <div>
		<h4>{JSON.stringify(secret)}</h4>
	</div>
}

export default Private;