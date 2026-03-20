using Microsoft.AspNetCore.Mvc;
namespace DotnetApp.Controllers;
public class AccountController : Controller {
    private static readonly Dictionary<string,string> Users = new() {
        {"legacy_alice","Tr0ub4dor&3"}, {"legacy_bob","correct-horse-battery"}
    };
    [HttpGet] public IActionResult Login() {
        if (HttpContext.Session.GetString("Username") != null) return RedirectToAction("Dashboard","Home");
        return View();
    }
    [HttpPost][ValidateAntiForgeryToken]
    public IActionResult Login(string username, string password) {
        if (Users.TryGetValue(username, out var pw) && pw == password) {
            HttpContext.Session.SetString("Username", username);
            return RedirectToAction("Dashboard","Home");
        }
        ViewBag.Error = "Invalid credentials";
        return View();
    }
    public IActionResult Logout() { HttpContext.Session.Clear(); return RedirectToAction("Login"); }
}
