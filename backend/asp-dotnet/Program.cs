using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using NidamResourceServer.Endpoints;

var builder = WebApplication.CreateBuilder(args);

// ---------------------------------------------------------------------
// Configuration — mirrors the shape of Nidam's application.yml.
// Every value can be overridden via environment variables (ASP.NET Core's
// hierarchical env-var binding, e.g. Nidam__ClientId=other) without
// touching appsettings.json — same intent as Nidam's ${...} placeholder
// resolution.
// ---------------------------------------------------------------------
var nidamConfig = builder.Configuration.GetSection("Nidam");
var host = nidamConfig["Host"] ?? "http://localhost";
var reverseProxyPort = nidamConfig["ReverseProxyPort"] ?? "7080";
var authorizationServerPrefix = nidamConfig["AuthorizationServerPrefix"] ?? "/auth";
var issuer = nidamConfig["Issuer"] ?? $"{host}:{reverseProxyPort}{authorizationServerPrefix}";
var expectedAudience = nidamConfig["ClientId"] ?? "client";
var resourceServerPort = nidamConfig["ResourceServerPort"] ?? "4003";
var profilePublicEndpoint = nidamConfig["ProfilePublicEndpoint"] ?? "/me";

builder.WebHost.UseUrls($"http://localhost:{resourceServerPort}");

// ---------------------------------------------------------------------
// JWT validation — equivalent to Nidam's SecurityConfig#jwtDecoder().
//
// Setting Authority triggers the same OIDC-discovery-then-JWKS flow
// Spring's JwtDecoders.fromIssuerLocation(issuer) performs: ASP.NET Core
// fetches "{issuer}/.well-known/openid-configuration", resolves jwks_uri,
// and validates signatures against the published keys, refreshing them
// automatically when the Authorization Server rotates keys.
// ---------------------------------------------------------------------
builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Authority = issuer;

        // Nidam's local Authorization Server runs over plain http;
        // relax the metadata-must-be-https requirement for local dev.
        // Set this to true (or drop the override) once issuer is https.
        options.RequireHttpsMetadata = !issuer.StartsWith("http://");

        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = issuer,

            // Equivalent to Nidam's custom AudienceValidator.
            ValidateAudience = true,
            ValidAudience = expectedAudience,

            // Equivalent to JwtTimestampValidator(Duration.ofSeconds(60)).
            // (.NET's own default is 5 minutes — set explicitly to match.)
            ClockSkew = TimeSpan.FromSeconds(60),

            // Principal name comes from "sub", matching Spring's
            // JwtAuthenticationToken#getName().
            NameClaimType = "sub",
        };

        // Keep raw JWT claim names ("sub", "email", "authorities", ...)
        // instead of ASP.NET Core's legacy Microsoft URN remapping, so
        // claim lookups below match the claim names Nidam actually issues.
        options.MapInboundClaims = false;

        options.Events = new JwtBearerEvents
        {
            // Equivalent to NidamAccessDeniedHandler — fires when an
            // authenticated caller fails a policy check (403).
            OnForbidden = async context =>
            {
                context.Response.ContentType = "application/json";
                context.Response.StatusCode = StatusCodes.Status403Forbidden;
                await context.Response.WriteAsJsonAsync(new
                {
                    status = 403,
                    code = "ACCESS_DENIED",
                    message = "You do not have sufficient permissions",
                });
            },
        };
    });

// ---------------------------------------------------------------------
// Authorization policies — equivalent to @PreAuthorize("hasAuthority(...)").
//
// Nidam issues authorities as a JSON array claim named "authorities".
// ASP.NET Core's JWT handler flattens JSON array claims into multiple
// Claims of the same type automatically, so RequireClaim's built-in
// "any of these values" semantics line up exactly with Spring's
// hasAuthority('a') or hasAuthority('b').
// ---------------------------------------------------------------------
builder.Services.AddAuthorization(options =>
{
    options.AddPolicy("ManageUsersOrProjects", policy =>
        policy.RequireClaim("authorities", "manage-users", "manage-projects"));

    options.AddPolicy("TopSecret", policy =>
        policy.RequireClaim("authorities", "top-secret"));
});

builder.Services.AddControllers();

var app = builder.Build();

app.UseAuthentication();
app.UseAuthorization();

// /me is mapped dynamically (not as an attribute-routed controller
// action) so its path can come from configuration — see MeEndpoint.cs.
app.MapMeEndpoint(profilePublicEndpoint);
app.MapControllers();

app.Run();