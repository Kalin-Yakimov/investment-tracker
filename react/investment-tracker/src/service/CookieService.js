import { Cookies } from "react-cookie";

const cookie = new Cookies();

class CookieService {
  static setCookie(name, value, options = {}) {
    cookie.set(name, value, { path: "/", ...options });
  }

  static getCookie(name) {
    return cookie.get(name);
  }

  static removeCookie(name) {
    cookie.remove(name, { path: "/" });
  }
}

export default CookieService;
