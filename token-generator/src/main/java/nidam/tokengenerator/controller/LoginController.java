package nidam.tokengenerator.controller;

import jakarta.servlet.http.HttpServletRequest;
import nidam.tokengenerator.config.SecurityConfigStaticKey;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.logging.Logger;

/**
 * Handles login page rendering and error messaging.
 */
@Controller
public class LoginController {

	private final Logger log = Logger.getLogger(LoginController.class.getName());

	/**
	 * Displays the login page.
	 * <p>
	 * If a login error was previously stored in the session, it is added to the model
	 * and removed from the session to prevent repeated display.
	 *
	 * @param request the current {@link HttpServletRequest}
	 * @param model the {@link Model} used to pass attributes to the view
	 * @return the name of the login view template
	 */
	@GetMapping("/login")
	public String login(HttpServletRequest  request, Model model) {
		String error = (String) request.getSession().getAttribute("LOGIN_ERROR");
//		log.info("LoginController.error: " + error);
		if (error != null) {
			model.addAttribute("LOGIN_ERROR", error);
			request.getSession().removeAttribute("LOGIN_ERROR");
		}
		return "login";
	}

}
