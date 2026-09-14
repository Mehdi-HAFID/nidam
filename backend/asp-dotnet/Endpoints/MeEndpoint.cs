using Microsoft.AspNetCore.Authorization;
using NidamResourceServer.Models;

namespace NidamResourceServer.Endpoints
{
    /// <summary>
    /// Public profile endpoint — equivalent to Nidam's <c>MeController</c>.
    ///
    /// Returns the caller's identity when a valid token was presented, or an
    /// anonymous payload otherwise. Never itself requires authentication,
    /// mirroring <c>profile-public-endpoint: /me</c> being <c>permitAll()</c>
    /// in Nidam's <c>SecurityConfig</c>.
    ///
    /// Mapped as a minimal API endpoint rather than an attribute-routed
    /// controller action specifically so the path can come from
    /// configuration at startup — attribute routes like
    /// <c>[HttpGet("/me")]</c> are compile-time constants in ASP.NET Core and
    /// can't read appsettings.json the way Spring's
    /// <c>@GetMapping("${profile-public-endpoint:/me}")</c> can.
    /// </summary>
    public static class MeEndpoint
    {
        public static void MapMeEndpoint(this WebApplication app, string path)
        {
            app.MapGet(path, (HttpContext http) =>
            {
                var user = http.User;
                if (user.Identity is not { IsAuthenticated: true })
                {
                    return Results.Ok(UserInfoDto.Anonymous);
                }

                var email = user.FindFirst("email")?.Value ?? string.Empty;
                var authorities = user.FindAll("authorities").Select(c => c.Value).ToArray();
                var exp = long.TryParse(user.FindFirst("exp")?.Value, out var expValue)
                    ? expValue
                    : long.MaxValue;

                return Results.Ok(new UserInfoDto(user.Identity.Name ?? string.Empty, email, authorities, exp));
            }).AllowAnonymous();
        }
    }
}
