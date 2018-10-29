import org.apache.commons.cli.*;

class ArgParser {
    private Options options = new Options();
    boolean isClient;
    String hostname;
    int port;
    long time;

    private void initOptions() {
        options.addOption(Option.builder("c").desc("Indicates this is the iperf client which should generate data")
                .hasArg(false).build());
        options.addOption(Option.builder("h").desc("The hostname or IP address of the iperf server")
                .hasArg().type(String.class).argName("hostname or IP address").build());
        options.addOption(Option.builder("p").desc("the port on which the host is waiting to waiting to consume data")
                .hasArg().type(Long.TYPE).argName("port").build());
        options.addOption(Option.builder("t").desc("duration in seconds for which data should be generated")
                .hasArg().type(Double.TYPE).argName("time").build());


        options.addOption(Option.builder("s").desc("Indicates this is the iperf server which should consume data")
                .hasArg(false).build());
    }

    private void errExit() {
        HelpFormatter formatter = new HelpFormatter();
        System.out.println("Error: missing or additional arguments");
        formatter.printHelp("Iperfer [options]", options);
        System.exit(1);
    }

    void parse(String[] args) {
        initOptions();

        CommandLine cmd;
        CommandLineParser parser = new DefaultParser();

        try {
            cmd = parser.parse(options, args);
            String[] restArgs = cmd.getArgs();

            if (restArgs.length != 0 || (cmd.hasOption("c") && cmd.hasOption("s")))
                errExit();

            if (cmd.hasOption("c")) {
                if (!cmd.hasOption("h") || !cmd.hasOption("p") || !cmd.hasOption("t"))
                    errExit();

                isClient = true;
                hostname = cmd.getOptionValue("h");
                time = Long.parseLong(cmd.getOptionValue("t"));
            } else if (cmd.hasOption("s")) {
                if (!cmd.hasOption("p") || cmd.hasOption("h") || cmd.hasOption("t"))
                    errExit();
                isClient = false;
            } else errExit();

            port = Integer.parseInt(cmd.getOptionValue("p"));
            if (port < 1024 || port > 65535) {
                System.err.println("Error: port number must be in the range of 1024 to 65535");
                System.exit(1);
            }

        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
