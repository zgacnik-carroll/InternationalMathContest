import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContestResultsSplitter {
    private static final String[] REQUIRED_COLUMNS = {
        "Institution",
        "Team Number",
        "City",
        "State/Province",
        "Country",
        "Advisor",
        "Problem",
        "Ranking"
    };

    public static void main(String[] args) {
        if (args.length > 3) {
            printUsage();
            return;
        }

        Path inputPath = Paths.get("2015.csv");
        Path institutionsOutput = Paths.get("Institutions.csv");
        Path teamsOutput = Paths.get("Teams.csv");

        if (args.length >= 1) {
            inputPath = Paths.get(args[0]);
        }
        if (args.length >= 2) {
            institutionsOutput = Paths.get(args[1]);
        }
        if (args.length == 3) {
            teamsOutput = Paths.get(args[2]);
        }

        if (!Files.exists(inputPath)) {
            System.err.println("Input file not found: " + inputPath);
            return;
        }

        ReadResult readResult = readRows(inputPath);
        if (!readResult.successful()) {
            System.err.println(readResult.message());
            return;
        }

        List<Map<String, String>> rows = readResult.rows();
        OutputData outputData = buildOutputs(rows);

        WriteResult institutionsWriteResult = writeCsv(
            institutionsOutput,
            List.of("Institution ID", "Institution Name", "City", "State/Province", "Country"),
            outputData.institutions
        );
        if (!institutionsWriteResult.successful()) {
            System.err.println(institutionsWriteResult.message());
            return;
        }

        WriteResult teamsWriteResult = writeCsv(
            teamsOutput,
            List.of("Team Number", "Advisor", "Problem", "Ranking", "Institution ID"),
            outputData.teams
        );
        if (!teamsWriteResult.successful()) {
            System.err.println(teamsWriteResult.message());
            return;
        }

        System.out.println("Read " + rows.size() + " team rows from " + inputPath + ".");
        System.out.println("Wrote " + outputData.institutions.size() + " institutions to " + institutionsOutput + ".");
        System.out.println("Wrote " + outputData.teams.size() + " teams to " + teamsOutput + ".");
    }

    private static void printUsage() {
        System.err.println(
            "Usage: java -cp src ContestResultsSplitter [input.csv] [institutions_output.csv] [teams_output.csv]"
        );
    }

    private static ReadResult readRows(Path inputPath) {
        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return ReadResult.failure("Input file is empty: " + inputPath);
            }

            List<String> header = parseCsvLine(headerLine);
            for (int i = 0; i < header.size(); i++) {
                header.set(i, normalizeHeader(header.get(i)));
            }

            List<String> missingColumns = findMissingColumns(header);
            if (!missingColumns.isEmpty()) {
                return ReadResult.failure(
                    "Input file is missing required columns: " + String.join(", ", missingColumns)
                );
            }

            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                List<String> values = parseCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    String value = i < values.size() ? values.get(i).trim() : "";
                    row.put(header.get(i), value);
                }
                rows.add(row);
            }
            return ReadResult.success(rows);
        } catch (IOException exception) {
            return ReadResult.failure("Unable to read input file " + inputPath + ": " + exception.getMessage());
        }
    }

    private static List<String> findMissingColumns(List<String> header) {
        List<String> missing = new ArrayList<>();
        for (String requiredColumn : REQUIRED_COLUMNS) {
            if (!header.contains(requiredColumn)) {
                missing.add(requiredColumn);
            }
        }
        return missing;
    }

    private static String normalizeHeader(String value) {
        return value.replace("\uFEFF", "").replace("ï»¿", "").trim();
    }

    private static OutputData buildOutputs(List<Map<String, String>> rows) {
        LinkedHashMap<InstitutionKey, String> institutionIds = new LinkedHashMap<>();
        List<Map<String, String>> institutions = new ArrayList<>();
        List<Map<String, String>> teams = new ArrayList<>();

        for (Map<String, String> row : rows) {
            InstitutionKey institutionKey = new InstitutionKey(
                row.get("Institution"),
                row.get("City"),
                row.get("State/Province"),
                row.get("Country")
            );

            String institutionId = institutionIds.get(institutionKey);
            if (institutionId == null) {
                institutionId = String.format("INST%04d", institutionIds.size() + 1);
                institutionIds.put(institutionKey, institutionId);

                Map<String, String> institution = new LinkedHashMap<>();
                institution.put("Institution ID", institutionId);
                institution.put("Institution Name", institutionKey.institutionName);
                institution.put("City", institutionKey.city);
                institution.put("State/Province", institutionKey.stateProvince);
                institution.put("Country", institutionKey.country);
                institutions.add(institution);
            }

            Map<String, String> team = new LinkedHashMap<>();
            team.put("Team Number", row.get("Team Number"));
            team.put("Advisor", row.get("Advisor"));
            team.put("Problem", row.get("Problem"));
            team.put("Ranking", row.get("Ranking"));
            team.put("Institution ID", institutionId);
            teams.add(team);
        }

        return new OutputData(institutions, teams);
    }

    private static WriteResult writeCsv(Path outputPath, List<String> header, List<Map<String, String>> rows) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(String.join(",", header));
            writer.newLine();

            for (Map<String, String> row : rows) {
                List<String> values = new ArrayList<>();
                for (String column : header) {
                    values.add(escapeCsv(row.getOrDefault(column, "")));
                }
                writer.write(String.join(",", values));
                writer.newLine();
            }
            return WriteResult.success();
        } catch (IOException exception) {
            return WriteResult.failure("Unable to write output file " + outputPath + ": " + exception.getMessage());
        }
    }

    private static String escapeCsv(String value) {
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (currentChar == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (currentChar == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }

        values.add(current.toString());
        return values;
    }

    private static final class OutputData {
        private final List<Map<String, String>> institutions;
        private final List<Map<String, String>> teams;

        private OutputData(List<Map<String, String>> institutions, List<Map<String, String>> teams) {
            this.institutions = institutions;
            this.teams = teams;
        }
    }

    private static final class ReadResult {
        private final List<Map<String, String>> rows;
        private final String message;

        private ReadResult(List<Map<String, String>> rows, String message) {
            this.rows = rows;
            this.message = message;
        }

        private static ReadResult success(List<Map<String, String>> rows) {
            return new ReadResult(rows, null);
        }

        private static ReadResult failure(String message) {
            return new ReadResult(null, message);
        }

        private boolean successful() {
            return message == null;
        }

        private List<Map<String, String>> rows() {
            return rows;
        }

        private String message() {
            return message;
        }
    }

    private static final class WriteResult {
        private final String message;

        private WriteResult(String message) {
            this.message = message;
        }

        private static WriteResult success() {
            return new WriteResult(null);
        }

        private static WriteResult failure(String message) {
            return new WriteResult(message);
        }

        private boolean successful() {
            return message == null;
        }

        private String message() {
            return message;
        }
    }

    private static final class InstitutionKey {
        private final String institutionName;
        private final String city;
        private final String stateProvince;
        private final String country;

        private InstitutionKey(String institutionName, String city, String stateProvince, String country) {
            this.institutionName = institutionName;
            this.city = city;
            this.stateProvince = stateProvince;
            this.country = country;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InstitutionKey)) {
                return false;
            }
            InstitutionKey that = (InstitutionKey) other;
            return institutionName.equals(that.institutionName)
                && city.equals(that.city)
                && stateProvince.equals(that.stateProvince)
                && country.equals(that.country);
        }

        @Override
        public int hashCode() {
            int result = institutionName.hashCode();
            result = 31 * result + city.hashCode();
            result = 31 * result + stateProvince.hashCode();
            result = 31 * result + country.hashCode();
            return result;
        }
    }
}
