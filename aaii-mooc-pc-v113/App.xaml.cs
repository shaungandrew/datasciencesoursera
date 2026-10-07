using System.IO;
using System.Net.Http;
using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using AAIIMoocPC.Services;

namespace AAIIMoocPC;

public partial class App : Application
{
    TextBlock? _status;

    public App()
    {
        DispatcherUnhandledException += (_, e) =>
        {
            Log(e.Exception);
            MessageBox.Show("UI error:\n\n" + e.Exception.Message + "\n\n" + CrashLog,
                "AAII MOOC PC V1.1.3", MessageBoxButton.OK, MessageBoxImage.Warning);
            e.Handled = true;
        };
        AppDomain.CurrentDomain.UnhandledException += (_, e) =>
        {
            if (e.ExceptionObject is Exception ex) Log(ex);
        };
    }

    private void Application_Startup(object sender, StartupEventArgs e)
    {
        ShutdownMode = ShutdownMode.OnExplicitShutdown;
        try
        {
            LogText("V1.1.3 diagnostic launcher startup");
            System.Windows.Media.RenderOptions.ProcessRenderMode =
                System.Windows.Interop.RenderMode.SoftwareOnly;

            var w = BuildLauncher();
            MainWindow = w;
            w.Show();
            LogText("Diagnostic launcher shown");
        }
        catch (Exception ex)
        {
            Log(ex);
            MessageBox.Show("Diagnostic launcher could not start.\n\n" + ex.Message +
                "\n\nCrash log:\n" + CrashLog,
                "AAII MOOC PC V1.1.3", MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    Window BuildLauncher()
    {
        var w = new Window
        {
            Title = "AAII MOOC PC V1.1.3 — Diagnostic Launcher",
            Width = 720,
            Height = 580,
            MinWidth = 620,
            MinHeight = 500,
            WindowStartupLocation = WindowStartupLocation.CenterScreen,
            Background = new SolidColorBrush(Color.FromRgb(244, 247, 251))
        };

        var root = new StackPanel { Margin = new Thickness(28) };
        root.Children.Add(new TextBlock
        {
            Text = "AAII MOOC PC V1.1.3",
            FontSize = 28,
            FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(Color.FromRgb(11, 43, 79))
        });
        root.Children.Add(new TextBlock
        {
            Text = "Diagnostic Launcher · no server connection is made at startup.",
            Margin = new Thickness(0, 8, 0, 20),
            FontSize = 14,
            Foreground = new SolidColorBrush(Color.FromRgb(71, 85, 105))
        });

        var open = NewButton("OPEN FULL MOOC UI");
        open.Click += (_, _) =>
        {
            try
            {
                LogText("Opening full MainWindow");
                var full = new MainWindow();
                full.Show();
                _status!.Text = "Full MOOC UI opened successfully.";
                LogText("Full MainWindow shown");
            }
            catch (Exception ex)
            {
                Log(ex);
                _status!.Text = "FULL UI ERROR: " + ex.Message;
                MessageBox.Show("Full MOOC UI failed, but the launcher is still running.\n\n" +
                    ex.Message + "\n\nSend this file:\n" + DesktopCrashLog,
                    "Full UI Error", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        };
        root.Children.Add(open);

        var test = NewButton("TEST SERVER API");
        test.Click += async (_, _) =>
        {
            _status!.Text = "Testing server...";
            try
            {
                using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
                var resp = await http.GetAsync("https://aaii.asia/edu/mooc/api/sources.php");
                var body = await resp.Content.ReadAsStringAsync();
                _status.Text = $"Server HTTP {(int)resp.StatusCode} · {resp.ReasonPhrase} · {Math.Min(body.Length, 99999)} bytes";
                LogText(_status.Text);
            }
            catch (Exception ex)
            {
                Log(ex);
                _status.Text = "SERVER TEST ERROR: " + ex.Message;
            }
        };
        root.Children.Add(test);

        var reset = NewButton("RESET LOCAL SETTINGS / LOGIN CACHE");
        reset.Click += (_, _) =>
        {
            try
            {
                if (File.Exists(AppSettings.FilePath)) File.Delete(AppSettings.FilePath);
                _status!.Text = "Local settings.json deleted. Restart after testing.";
                LogText("Local settings reset");
            }
            catch (Exception ex) { Log(ex); _status!.Text = ex.Message; }
        };
        root.Children.Add(reset);

        var log = NewButton("OPEN CRASH LOG FOLDER");
        log.Click += (_, _) =>
        {
            try
            {
                Directory.CreateDirectory(AppSettings.Root);
                Process.Start(new ProcessStartInfo("explorer.exe", AppSettings.Root) { UseShellExecute = true });
            }
            catch (Exception ex) { Log(ex); _status!.Text = ex.Message; }
        };
        root.Children.Add(log);

        _status = new TextBlock
        {
            Text = "Launcher is running. First click OPEN FULL MOOC UI.",
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 20, 0, 0),
            Padding = new Thickness(14),
            FontSize = 14,
            Foreground = new SolidColorBrush(Color.FromRgb(15, 92, 173)),
            Background = new SolidColorBrush(Color.FromRgb(232, 245, 255))
        };
        root.Children.Add(_status);

        root.Children.Add(new TextBlock
        {
            Text = "Crash log: " + DesktopCrashLog,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 12, 0, 0),
            FontSize = 12,
            Foreground = new SolidColorBrush(Color.FromRgb(100, 116, 139))
        });

        w.Content = new ScrollViewer { Content = root };
        w.Closing += (_, e) =>
        {
            if (MessageBox.Show("Exit AAII MOOC PC diagnostic launcher?", "Exit",
                MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes)
            {
                e.Cancel = true;
                return;
            }
            Shutdown();
        };
        return w;
    }

    Button NewButton(string text) => new()
    {
        Content = text,
        Height = 48,
        Margin = new Thickness(0, 6, 0, 6),
        Background = new SolidColorBrush(Color.FromRgb(15, 92, 173)),
        Foreground = Brushes.White,
        FontWeight = FontWeights.SemiBold,
        BorderThickness = new Thickness(0)
    };

    public static string CrashLog => Path.Combine(AppSettings.Root, "crash.log");
    public static string DesktopCrashLog =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
            "AAII_MOOC_PC_CRASH.txt");

    public static void Log(Exception ex) =>
        WriteLog("==== " + DateTime.Now + " ====\n" + ex + "\n");

    public static void LogText(string text) =>
        WriteLog("==== " + DateTime.Now + " ==== " + text + "\n");

    static void WriteLog(string text)
    {
        try
        {
            Directory.CreateDirectory(AppSettings.Root);
            File.AppendAllText(CrashLog, text);
        }
        catch { }
        try { File.AppendAllText(DesktopCrashLog, text); } catch { }
    }
}
