import { jwtDecode } from "jwt-decode";
import UserService from "../service/UserService";
import CookieService from "../service/CookieService";

const checkTokenExpiration = (navigate) => {
  const token = UserService.getToken();
  if (token) {
    const decodedToken = jwtDecode(token);
    const currentTime = Date.now() / 1000;
    if (decodedToken.exp < currentTime) {
      UserService.removeToken();
      CookieService.removeCookie("user_token");
      alert("Session expired. Please log in again.");
      navigate("/login");
    }
  }
};

export default checkTokenExpiration;
