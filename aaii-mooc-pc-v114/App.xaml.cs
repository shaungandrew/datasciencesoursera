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
    Window? _launcher;
    MainWindow? _full;
    TextBlock? _status;
    bool _exiting;

    public App()
    {
        DispatcherUnhandledException += (_, e) =>
        {
            Log(e.Exception);
            MessageBox.Show(
                "UI error:\n\n" + e.Exception.Message +
                "\n\nCrash log:\n" + DesktopCrashLog,
                "AAII MOOC PC V1.1.4",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
            e.Handled = true;
        };
        AppDomain.CurrentDomain.UnhandledException += (_, e) =>
        {
            if (e.ExceptionObject is Exception ex) Log(ex);
        };
        TaskScheduler.UnobservedTaskException += (_, e) =>
        {
            Log(e.Exception);
            e.SetObserved();
        };
        Exit += (_, _) => LogText("Application Exit event");
    }

    private void Application_Startup(object sender, StartupEventArgs e)
    {
        ShutdownMode = ShutdownMode.OnExplicitShutdown;
        try
        {
            LogText("V1.1.4 startup");
            RenderOptions.ProcessRenderMode =
                System.Windows.Interop.RenderMode.SoftwareOnly;

            _launcher = BuildLauncher();
            MainWindow = _launcher;
            _launcher.Show();
            LogText("Launcher shown");
        }
        catch (Exception ex)
        {
            Log(ex);
            MessageBox.Show(
                "AAII MOOC PC V1.1.4 could not start.\n\n" +
                ex.Message + "\n\nCrash log:\n" + DesktopCrashLog,
                "AAII MOOC PC V1.1.4",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    Window BuildLauncher()
    {
        var w = new Window
        {
            Title = "AAII MOOC PC V1.1.4 — Stable Launcher",
            Width = 720,
            Height = 600,
            MinWidth = 620,
            MinHeight = 500,
            WindowStartupLocation = WindowStartupLocation.CenterScreen,
            Background = Brush("#F4F7FB")
        };

        var root = new StackPanel { Margin = new Thickness(28) };
        root.Children.Add(new TextBlock
        {
            Text = "AAII MOOC PC V1.1.4",
            FontSize = 28,
            FontWeight = FontWeights.Bold,
            Foreground = Brush("#0B2B4F")
        });
        root.Children.Add(new TextBlock
        {
            Text = "Stable guarded mode · the launcher stays alive if the full MOOC window closes.",
            Margin = new Thickness(0, 8, 0, 20),
            FontSize = 14,
            TextWrapping = TextWrapping.Wrap,
            Foreground = Brush("#475569")
        });

        var open = NewButton("OPEN FULL MOOC UI");
        open.Click += (_, _) => OpenFullUi();
        root.Children.Add(open);

        var test = NewButton("TEST SERVER API");
        test.Click += async (_, _) =>
        {
            SetStatus("Testing server...");
            try
            {
                using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
                var resp = await http.GetAsync("https://aaii.asia/edu/mooc/api/sources.php");
                var body = await resp.Content.ReadAsStringAsync();
                SetStatus($"Server HTTP {(int)resp.StatusCode} · {resp.ReasonPhrase} · {body.Length:N0} bytes");
                LogText("Server test: " + _status!.Text);
            }
            catch (Exception ex)
            {
                Log(ex);
                SetStatus("SERVER TEST ERROR: " + ex.Message);
            }
        };
        root.Children.Add(test);

        var reset = NewButton("RESET LOCAL LOGIN / SETTINGS CACHE");
        reset.Click += (_, _) =>
        {
            try
            {
                if (File.Exists(AppSettings.FilePath))
                    File.Delete(AppSettings.FilePath);
                SetStatus("Local settings.json deleted. Open Full MOOC UI and sign in again.");
                LogText("Local settings reset");
            }
            catch (Exception ex)
            {
                Log(ex);
                SetStatus(ex.Message);
            }
        };
        root.Children.Add(reset);

        var folder = NewButton("OPEN CRASH LOG FOLDER");
        folder.Click += (_, _) =>
        {
            try
            {
                Directory.CreateDirectory(AppSettings.Root);
                Process.Start(new ProcessStartInfo("explorer.exe", AppSettings.Root)
                {
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                Log(ex);
                SetStatus(ex.Message);
            }
        };
        root.Children.Add(folder);

        _status = new TextBlock
        {
            Text = "Ready. Click OPEN FULL MOOC UI.",
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 20, 0, 0),
            Padding = new Thickness(14),
            FontSize = 14,
            Foreground = Brush("#0F5CAD"),
            Background = Brush("#E8F5FF")
        };
        root.Children.Add(_status);

        root.Children.Add(new TextBlock
        {
            Text = "Crash log: " + DesktopCrashLog,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 12, 0, 0),
            FontSize = 12,
            Foreground = Brush("#64748B")
        });

        w.Content = new ScrollViewer { Content = root };
        w.Closing += (_, e) =>
        {
            LogText("Launcher Closing");
            if (_exiting) return;

            if (MessageBox.Show(
                    "Exit AAII MOOC PC V1.1.4?",
                    "Exit",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question) != MessageBoxResult.Yes)
            {
                e.Cancel = true;
                return;
            }

            e.Cancel = true;
            _exiting = true;
            LogText("User confirmed application exit");
            try
            {
                if (_full != null)
                {
                    _full.ForceCloseForAppExit = true;
                    _full.Close();
                }
            }
            catch (Exception ex) { Log(ex); }
            Shutdown();
        };

        return w;
    }

    void OpenFullUi()
    {
        try
        {
            if (_full != null)
            {
                if (_full.IsVisible)
                {
                    _full.Activate();
                    return;
                }
                _full = null;
            }

            LogText("Opening full MainWindow");
            var full = new MainWindow();
            _full = full;

            full.Loaded += (_, _) =>
            {
                LogText("Full MainWindow Loaded");
                SetStatus("Full MOOC UI loaded.");
                try { _launcher?.Hide(); } catch { }
            };
            full.Activated += (_, _) => LogText("Full MainWindow Activated");
            full.Deactivated += (_, _) => LogText("Full MainWindow Deactivated");
            full.Closing += (_, e) =>
                LogText("Full MainWindow Closing · cancel=" + e.Cancel);
            full.Closed += (_, _) =>
            {
                LogText("Full MainWindow Closed");
                _full = null;
                if (!_exiting)
                {
                    try
                    {
                        _launcher?.Show();
                        _launcher?.Activate();
                        SetStatus("Full MOOC UI closed. Launcher remained open.");
                    }
                    catch (Exception ex) { Log(ex); }
                }
            };

            full.Show();
            LogText("Full MainWindow Show returned");
        }
        catch (Exception ex)
        {
            Log(ex);
            SetStatus("FULL UI ERROR: " + ex.Message);
            MessageBox.Show(
                "Full MOOC UI failed, but the launcher will stay open.\n\n" +
                ex.Message + "\n\nSend this file:\n" + DesktopCrashLog,
                "AAII MOOC PC V1.1.4",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
    }

    void SetStatus(string text)
    {
        if (_status != null) _status.Text = text;
    }

    Button NewButton(string text) => new()
    {
        Content = text,
        Height = 48,
        Margin = new Thickness(0, 6, 0, 6),
        Background = Brush("#0F5CAD"),
        Foreground = Brushes.White,
        FontWeight = FontWeights.SemiBold,
        BorderThickness = new Thickness(0)
    };

    static SolidColorBrush Brush(string hex) =>
        (SolidColorBrush)new BrushConverter().ConvertFromString(hex)!;

    public static string CrashLog =>
        Path.Combine(AppSettings.Root, "crash.log");

    public static string DesktopCrashLog =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
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

        try { File.AppendAllText(DesktopCrashLog, text); }
        catch { }
    }
}
