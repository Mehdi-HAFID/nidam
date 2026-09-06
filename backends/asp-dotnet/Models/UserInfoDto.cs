namespace NidamResourceServer.Models
{

    /// <summary>
    /// Mirrors Nidam's <c>MeController.UserInfoDto</c> record exactly, field for
    /// field, so a frontend written against Nidam's Spring resource server
    /// doesn't need to branch on which backend it's talking to.
    /// </summary>
    /// <param name="Username">Principal name (the "sub" claim, by default).</param>
    /// <param name="Email">OpenID "email" claim.</param>
    /// <param name="Authorities">Authorities resolved from the "authorities" claim.</param>
    /// <param name="Exp">Seconds since 1970-01-01T00:00:00Z UTC when the access token expires.</param>
    public record UserInfoDto(string Username, string Email, IReadOnlyList<string> Authorities, long Exp)
    {
        public static readonly UserInfoDto Anonymous = new(string.Empty, string.Empty, Array.Empty<string>(), long.MaxValue);
    }
}
