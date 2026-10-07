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
                "\n\nDiagnostic log:\n" + DesktopCrashLog,
                "AAII MOOC PC V1.1.5",
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

        AppDomain.CurrentDomain.ProcessExit += (_, _) =>
            LogText("AppDomain ProcessExit");

        Exit += (_, e) =>
            LogText("WPF Application Exit · code=" + e.ApplicationExitCode);
    }

    private void Application_Startup(object sender, StartupEventArgs e)
    {
        ShutdownMode = ShutdownMode.OnExplicitShutdown;

        try
        {
            LogText(
                "V1.1.5 startup · " +
                (Environment.Is64BitProcess ? "x64" : "x86") +
                " · OS=" + Environment.OSVersion +
                " · CLR=" + Environment.Version +
                " · Base=" + AppContext.BaseDirectory);

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
                "AAII MOOC PC V1.1.5 could not start.\n\n" +
                ex.Message + "\n\nDiagnostic log:\n" + DesktopCrashLog,
                "AAII MOOC PC V1.1.5",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    Window BuildLauncher()
    {
        var w = new Window
        {
            Title = "AAII MOOC PC V1.1.5 — Compatibility Watchdog",
            Width = 720,
            Height = 620,
            MinWidth = 620,
            MinHeight = 520,
            WindowStartupLocation = WindowStartupLocation.CenterScreen,
            Background = Brush("#F4F7FB")
        };

        var root = new StackPanel { Margin = new Thickness(28) };

        root.Children.Add(new TextBlock
        {
            Text = "AAII MOOC PC V1.1.5",
            FontSize = 28,
            FontWeight = FontWeights.Bold,
            Foreground = Brush("#0B2B4F")
        });

        root.Children.Add(new TextBlock
        {
            Text = "Compatibility mode · launcher remains open · server does not connect until you sign in or press TEST SERVER.",
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

        var logs = NewButton("OPEN DIAGNOSTIC LOG FOLDER");
        logs.Click += (_, _) =>
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
        root.Children.Add(logs);

        _status = new TextBlock
        {
            Text = "Ready. Keep this launcher open and click OPEN FULL MOOC UI.",
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
            Text =
                "For a disappearing process, start the program with START_DIAGNOSTIC.cmd. " +
                "It records the process exit code plus recent Windows Application Error, .NET Runtime, WER and Defender events.",
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
                    "Exit AAII MOOC PC V1.1.5?",
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
                LogText(
                    "Full MainWindow Loaded · WorkingSet=" +
                    (Environment.WorkingSet / 1024 / 1024) + " MB");
                SetStatus("Full MOOC UI loaded. Launcher remains open for protection.");
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
                        SetStatus("Full MOOC UI closed. Launcher is still running.");
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
                "Full MOOC UI failed, but the launcher stays open.\n\n" +
                ex.Message + "\n\nSend this file:\n" + DesktopCrashLog,
                "AAII MOOC PC V1.1.5",
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
