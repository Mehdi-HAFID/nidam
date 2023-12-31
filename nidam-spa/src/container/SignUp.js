import * as React from 'react';
import {useState} from 'react';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import CssBaseline from '@mui/material/CssBaseline';
import TextField from '@mui/material/TextField';
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import Link from '@mui/material/Link';
import Grid from '@mui/material/Grid';
import Box from '@mui/material/Box';
import AccountBoxIcon from '@mui/icons-material/AccountBox';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';

import {checkValidity} from "../other/utility";

function Copyright(props) {
	return (
		<Typography variant="body2" color="text.secondary" align="center" {...props}>
			{'Copyright © '}
			<Link color="inherit" href="https://tigmat.io/">
				Your Website
			</Link>{' '}
			{new Date().getFullYear()}
			{'.'}
		</Typography>
	);
}


const changeText = (inputTextState, value, label) => {
	const updatedInputTextState = {...inputTextState};
	updatedInputTextState.value = value;
	const [valid, message] = checkValidity(updatedInputTextState, label);
	updatedInputTextState.valid = valid;
	updatedInputTextState.validationMessage = message;
	return updatedInputTextState;
}

const SignUp = (props) => {

	const [email, setEmail] = useState({
		value: "",
		validation: {
			required: true,
			maxLength: 320,
			isEmail: true
		},
		valid: true,
		validationMessage: null
	});

	const [password, setPassword] = useState({
		value: '',
		validation: {
			required: true,
			minLength: 4,
			maxLength: 255
		},
		valid: false,
		validationMessage: "Invalid Password"
	});

	const [confirmPassword, setConfirmPassword] = useState({
		value: '',
		validation: {
			required: true,
			minLength: 4,
			maxLength: 255
		},
		valid: false
	});

	const [touchedPassword, setTouchedPassword] = useState(false);

	const emailChangeHandler = (event) => {
		const updatedEmail = changeText(email, event.target.value, "Email");
		setEmail(updatedEmail);
	}

	const passwordChangeHandler = (event) => {
		setTouchedPassword(true);
		const updatedPassword = changeText(password, event.target.value, "Password");

		if (!updatedPassword.valid) {
			setPassword(updatedPassword);
		} else {
			if (updatedPassword.value !== confirmPassword.value) {
				updatedPassword.valid = false;
				updatedPassword.validationMessage = "Password and Confirmation don't match";
				setPassword(updatedPassword);
			} else {
				updatedPassword.valid = true;
				updatedPassword.validationMessage = null;
				setPassword(updatedPassword);

				const updatedConfirmPassword = {...confirmPassword};
				updatedConfirmPassword.valid = true;
				updatedConfirmPassword.validationMessage = null;
				setConfirmPassword(updatedConfirmPassword);
			}
		}

	}

	const confirmPasswordChangeHandler = (event) => {
		const updatedConfirmPassword = changeText(confirmPassword, event.target.value, "Password Confirmation");

		if (!updatedConfirmPassword.valid) {
			setConfirmPassword(updatedConfirmPassword);
		} else {
			if (updatedConfirmPassword.value !== password.value) {
				updatedConfirmPassword.valid = false;
				updatedConfirmPassword.validationMessage = "Password and Confirmation don't match";
				setConfirmPassword(updatedConfirmPassword);
			} else {
				updatedConfirmPassword.valid = true;
				updatedConfirmPassword.validationMessage = null;
				setConfirmPassword(updatedConfirmPassword);

				const updatedPassword = {...password};
				updatedPassword.valid = true;
				updatedPassword.validationMessage = null;
				setPassword(updatedPassword);
			}
		}
	}


	const checkAllInputsValidity = () => {
		let allValid = true;
		const updatedEmail = changeText(email, email.value, "Email");
		setEmail(updatedEmail);
		allValid = allValid && updatedEmail.valid;

		// TODO test
		const updatedPassword = changeText(password, password.value, "Password");
		setPassword(updatedPassword);
		allValid = allValid && password.valid;

		const updatedConfirmPassword = changeText(confirmPassword, confirmPassword.value, "Password Confirmation");
		setConfirmPassword(updatedConfirmPassword);
		allValid = allValid && confirmPassword.valid;
		return allValid;
	}

	const handleSubmit = (event) => {
		event.preventDefault();

		setTouchedPassword(true);

		if (!checkAllInputsValidity()) {
			return;
		}

		console.log({
			email: email.value,
			password: password.value,
		});
	};

	return (
		<Container component="main" maxWidth="xs">
			<CssBaseline/>
			<Box
				sx={{
					marginTop: 8,
					display: 'flex',
					flexDirection: 'column',
					alignItems: 'center',
				}}
			>
				<Avatar sx={{m: 1, bgcolor: 'info.main'}}>
					<AccountBoxIcon/>
				</Avatar>
				<Typography component="h1" variant="h5">
					Sign up
				</Typography>
				<Box sx={{mt: 1}}>
					<TextField
						margin="normal"
						required
						fullWidth
						id="email"
						label="Email Address"
						name="email"
						autoComplete="email"
						autoFocus
						value={email.value}
						onChange={emailChangeHandler}
						helperText={!email.valid ? email.validationMessage : ""}
						error={!email.valid}
					/>
					<TextField
						margin="normal"
						required
						fullWidth
						name="password"
						label="Password"
						type="password"
						id="password"
						autoComplete="new-password"
						value={password.value}
						onChange={passwordChangeHandler}
						helperText={(touchedPassword && !password.valid) ? password.validationMessage : ""}
						error={touchedPassword && !password.valid}
					/>
					<TextField
						margin="normal"
						required
						fullWidth
						name="password-confirmation"
						label="Confirm Password"
						type="password"
						id="password-confirmation"
						autoComplete="new-password"
						value={confirmPassword.value}
						onChange={confirmPasswordChangeHandler}
						helperText={(touchedPassword && !confirmPassword.valid) ? confirmPassword.validationMessage : ""}
						error={touchedPassword && !confirmPassword.valid}
					/>
					{/*<FormControlLabel*/}
					{/*	control={<Checkbox value="remember" color="primary"/>}*/}
					{/*	label="Remember me"*/}
					{/*/>*/}
					<FormControlLabel control={<Checkbox value="remember" color="primary"/>}
					                  label={<>I agree to <a href="https://tigmat.io/legal/terms.html" target="_blank" className="mr-1"
					                                         rel="noopener noreferrer">the
						                  Terms &amp; Conditions</a></>}>

					</FormControlLabel>
					<Button
						fullWidth
						variant="contained"
						sx={{mt: 3, mb: 2}}
						onClick={handleSubmit}
					>
						Sign In
					</Button>
					<Grid container justifyContent="flex-end">
						<Grid item>
							<Link href="#" variant="body2">
								Already have an account? Sign in
							</Link>
						</Grid>
					</Grid>
				</Box>
			</Box>
			<Copyright sx={{mt: 8, mb: 4}}/>
		</Container>
	);
};
export default SignUp;
