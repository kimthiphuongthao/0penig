using Microsoft.AspNetCore.Mvc;
namespace DotnetApp.Controllers;
public class HomeController : Controller {
    public IActionResult Index() => RedirectToAction("Dashboard");
    public IActionResult Dashboard() {
        var user = HttpContext.Session.GetString("Username");
        if (user == null) return RedirectToAction("Login","Account");
        ViewBag.Username = user;
        return View();
    }
}
