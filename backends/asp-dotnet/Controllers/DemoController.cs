using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace NidamResourceServer.Controllers
{
    /// <summary>
    /// Demo REST controller used to validate authority-based access control,
    /// equivalent to Nidam's <c>DemoController</c>.
    ///
    /// <list type="bullet">
    /// <item><description><c>/demo</c> — accessible with either
    /// <c>manage-users</c> or <c>manage-projects</c>.</description></item>
    /// <item><description><c>/top-secret</c> — accessible only with
    /// <c>top-secret</c>, which no default user holds.</description></item>
    /// </list>
    /// </summary>
    [ApiController]
    public class DemoController : ControllerBase
    {
        /// <summary>
        /// Accessible to callers with either <c>manage-users</c> or
        /// <c>manage-projects</c>. Returns the caller's claims, mirroring how
        /// Nidam's version returns the full JwtAuthenticationToken for
        /// inspection.
        /// </summary>
        [HttpGet("/demo")]
        [Authorize(Policy = "ManageUsersOrProjects")]
        public IActionResult AllowedResource()
        {
            var claims = User.Claims
                .GroupBy(c => c.Type)
                .ToDictionary(g => g.Key, g => g.Select(c => c.Value).ToArray());

            return Ok(claims);
        }

        /// <summary>
        /// Highly restricted endpoint requiring <c>top-secret</c> authority.
        /// Used to validate access-denial behavior when insufficient
        /// permissions are present.
        /// </summary>
        [HttpGet("/top-secret")]
        [Authorize(Policy = "TopSecret")]
        public IActionResult ForbiddenResource()
        {
            return Ok("Top secret information");
        }
    }
}
