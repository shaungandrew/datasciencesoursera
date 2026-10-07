using AAIIMoocPC.Services;
using Microsoft.Web.WebView2.Core;
using System.Diagnostics;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace AAIIMoocPC;

public partial class DirectCourseDetailWindow : Window
{
    readonly ApiClient _api;
    readonly int _id;
    string _currentExternalUrl = "";
    string _currentPlayerUrl = "";
    bool _playerReady;

    public DirectCourseDetailWindow(ApiClient api, int id)
    {
        InitializeComponent();
        _api = api;
        _id = id;
        Loaded += async (_, _) =>
        {
            await InitPlayerAsync();
            await LoadAsync();
        };
    }

    async Task InitPlayerAsync()
    {
        try
        {
            PlayerStatus.Text = "Starting in-app player…";
            await Player.EnsureCoreWebView2Async();
            var core = Player.CoreWebView2;
            if (core == null) throw new InvalidOperationException("WebView2 did not initialize.");

            core.Settings.AreDefaultContextMenusEnabled = true;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.IsZoomControlEnabled = true;
            core.Settings.AreBrowserAcceleratorKeysEnabled = true;

            core.AddWebResourceRequestedFilter("https://www.youtube.com/*", CoreWebView2WebResourceContext.All);
            core.AddWebResourceRequestedFilter("https://www.youtube-nocookie.com/*", CoreWebView2WebResourceContext.All);
            core.WebResourceRequested += (_, e) =>
            {
                try
                {
                    var uri = e.Request.Uri ?? "";
                    if (uri.Contains("youtube.com", StringComparison.OrdinalIgnoreCase) ||
                        uri.Contains("youtube-nocookie.com", StringComparison.OrdinalIgnoreCase))
                    {
                        e.Request.Headers.SetHeader("Referer", "https://aaii.asia/");
                    }
                }
                catch { }
            };

            core.NavigationCompleted += (_, e) =>
            {
                if (e.IsSuccess)
                {
                    PlayerStatus.Text = "Playing inside AAII MOOC PC";
                }
                else
                {
                    PlayerStatus.Text = $"Player error: {e.WebErrorStatus}. Use Reload or Open External.";
                }
            };

            _playerReady = true;
            PlayerStatus.Text = "Player ready — choose a lesson below.";
        }
        catch (Exception ex)
        {
            _playerReady = false;
            PlayerStatus.Text = "In-app player is unavailable: " + ex.Message;
            App.Log(ex);
        }
    }

    async Task LoadAsync()
    {
        try
        {
            Body.Children.Clear();
            Body.Children.Add(T("Loading course…", 13, "#64748B", false));
            var r = await _api.GetAsync("course.php?id=" + _id);
            Render(r);
        }
        catch (Exception ex)
        {
            Body.Children.Clear();
            Body.Children.Add(T(ex.Message, 13, "#B91C1C", false));
            App.Log(ex);
        }
    }

    void Render(JsonObject r)
    {
        Body.Children.Clear();
        var c = r["course"] as JsonObject;
        var d = r["details"] as JsonObject;
        var stats = r["stats"] as JsonObject;
        if (c == null) return;

        TitleText.Text = ApiClient.Str(d, "title_en", ApiClient.Str(c, "title", "Course"));
        CategoryText.Text = ApiClient.Str(c, "category_title", "Course");

        var desc = ApiClient.Str(d, "description_en", ApiClient.Str(c, "description"));
        if (desc.Length > 0) Body.Children.Add(Card("Course Overview", desc));
        if (stats != null)
            Body.Children.Add(Card("Course Includes",
                $"{ApiClient.Int(stats, "modules")} modules · {ApiClient.Int(stats, "videos")} videos · {ApiClient.Int(stats, "resources")} resources"));

        var mods = r["modules"] as JsonArray;
        if (mods == null) return;

        int i = 0;
        foreach (var n in mods)
        {
            if (n is not JsonObject m) continue;
            i++;
            var box = Box();
            var sp = new StackPanel { Margin = new Thickness(16) };
            sp.Children.Add(T($"{i}. {ApiClient.Str(m, "title", "Module")}", 18, "#0B2B4F", true));

            var ls = m["lessons"] as JsonArray;
            if (ls != null)
            {
                foreach (var ln in ls)
                {
                    if (ln is not JsonObject l) continue;
                    var lessonTitle = ApiClient.Str(l, "title", "Lesson");
                    var embed = ApiClient.Str(l, "embed_url");
                    var drive = ApiClient.Str(l, "drive_url");
                    var mime = ApiClient.Str(l, "mime_type");

                    var row = new Button
                    {
                        Content = (ApiClient.Int(l, "completed") > 0 ? "✓ " : "▶ ") + lessonTitle,
                        HorizontalContentAlignment = HorizontalAlignment.Left,
                        Height = 42,
                        Margin = new Thickness(0, 5, 0, 0),
                        ToolTip = "Play inside AAII MOOC PC"
                    };

                    row.Click += (_, _) => PlayInside(lessonTitle, embed, drive, mime);
                    sp.Children.Add(row);
                }
            }

            var rs = m["resources"] as JsonArray;
            if (rs != null)
            {
                foreach (var rn in rs)
                {
                    if (rn is not JsonObject rr) continue;
                    var resourceTitle = ApiClient.Str(rr, "title", "Resource");
                    var u = ApiClient.Str(rr, "drive_url");
                    var mime = ApiClient.Str(rr, "mime_type");
                    var rb = new Button
                    {
                        Content = "⇩ " + resourceTitle,
                        HorizontalContentAlignment = HorizontalAlignment.Left,
                        Height = 40,
                        Margin = new Thickness(0, 5, 0, 0)
                    };

                    if (mime.StartsWith("video/", StringComparison.OrdinalIgnoreCase))
                        rb.Click += (_, _) => PlayInside(resourceTitle, u, u, mime);
                    else
                        rb.Click += (_, _) => OpenExternal(u);
                    sp.Children.Add(rb);
                }
            }

            box.Child = sp;
            Body.Children.Add(box);
        }
    }

    void PlayInside(string title, string embedUrl, string fallbackUrl, string mime)
    {
        var raw = !string.IsNullOrWhiteSpace(embedUrl) ? embedUrl : fallbackUrl;
        var playable = NormalizePlayableUrl(raw, mime);
        var external = !string.IsNullOrWhiteSpace(fallbackUrl) ? fallbackUrl : raw;

        PlayerTitle.Text = title;
        _currentPlayerUrl = playable;
        _currentExternalUrl = external;

        if (string.IsNullOrWhiteSpace(playable))
        {
            PlayerStatus.Text = "No playable URL was provided by the server.";
            return;
        }

        PlayerPlaceholder.Visibility = Visibility.Collapsed;

        if (!_playerReady || Player.CoreWebView2 == null)
        {
            PlayerStatus.Text = "WebView2 player is unavailable. Opening in your browser instead.";
            OpenExternal(external);
            return;
        }

        try
        {
            PlayerStatus.Text = playable.Contains("youtube", StringComparison.OrdinalIgnoreCase)
                ? "Loading YouTube video inside software…"
                : playable.Contains("drive.google.com", StringComparison.OrdinalIgnoreCase)
                    ? "Loading Google Drive video inside software…"
                    : "Loading video inside software…";

            if (playable.Contains("youtube", StringComparison.OrdinalIgnoreCase))
            {
                var req = Player.CoreWebView2.Environment.CreateWebResourceRequest(
                    playable,
                    "GET",
                    null,
                    "Referer: https://aaii.asia/\r\n");
                Player.CoreWebView2.NavigateWithWebResourceRequest(req);
            }
            else
            {
                Player.CoreWebView2.Navigate(playable);
            }
        }
        catch (Exception ex)
        {
            App.Log(ex);
            PlayerStatus.Text = "Could not load in-app player: " + ex.Message;
        }
    }

    static string NormalizePlayableUrl(string raw, string mime)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "";
        raw = raw.Trim();

        var yt = ExtractYouTubeId(raw);
        if (!string.IsNullOrWhiteSpace(yt))
            return "https://www.youtube-nocookie.com/embed/" + Uri.EscapeDataString(yt) +
                   "?autoplay=1&rel=0&playsinline=1&modestbranding=1";

        var driveId = ExtractDriveId(raw);
        if (!string.IsNullOrWhiteSpace(driveId))
            return "https://drive.google.com/file/d/" + Uri.EscapeDataString(driveId) + "/preview";

        return raw;
    }

    static string ExtractYouTubeId(string url)
    {
        try
        {
            var patterns = new[]
            {
                @"youtu\.be/([A-Za-z0-9_-]{6,})",
                @"youtube(?:-nocookie)?\.com/(?:embed|shorts)/([A-Za-z0-9_-]{6,})",
                @"[?&]v=([A-Za-z0-9_-]{6,})"
            };
            foreach (var p in patterns)
            {
                var m = Regex.Match(url, p, RegexOptions.IgnoreCase);
                if (m.Success) return m.Groups[1].Value;
            }
        }
        catch { }
        return "";
    }

    static string ExtractDriveId(string url)
    {
        try
        {
            var patterns = new[]
            {
                @"drive\.google\.com/file/d/([A-Za-z0-9_-]{10,})",
                @"[?&]id=([A-Za-z0-9_-]{10,})"
            };
            foreach (var p in patterns)
            {
                var m = Regex.Match(url, p, RegexOptions.IgnoreCase);
                if (m.Success) return m.Groups[1].Value;
            }
        }
        catch { }
        return "";
    }

    void Reload_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_currentPlayerUrl)) return;
        PlayInside(PlayerTitle.Text, _currentPlayerUrl, _currentExternalUrl, "");
    }

    void External_Click(object sender, RoutedEventArgs e) => OpenExternal(_currentExternalUrl);

    void OpenExternal(string u)
    {
        if (string.IsNullOrWhiteSpace(u)) return;
        try { Process.Start(new ProcessStartInfo(u) { UseShellExecute = true }); }
        catch (Exception ex) { App.Log(ex); }
    }

    Border Card(string title, string body)
    {
        var b = Box();
        b.Child = new StackPanel
        {
            Margin = new Thickness(16),
            Children =
            {
                T(title,19,"#0B2B4F",true),
                T(body,13,"#64748B",false,new Thickness(0,9,0,0))
            }
        };
        return b;
    }

    Border Box() => new()
    {
        Background = Brush("#FFFFFF"),
        BorderBrush = Brush("#DCE6F1"),
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(16),
        Margin = new Thickness(0, 0, 0, 12)
    };

    TextBlock T(string text, double size, string color, bool bold, Thickness? m = null) => new()
    {
        Text = text,
        FontSize = size,
        Foreground = Brush(color),
        FontWeight = bold ? FontWeights.Bold : FontWeights.Normal,
        TextWrapping = TextWrapping.Wrap,
        Margin = m ?? new Thickness(0),
        LineHeight = 21
    };

    static SolidColorBrush Brush(string h) =>
        (SolidColorBrush)new BrushConverter().ConvertFromString(h)!;
}
