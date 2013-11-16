/** TODO: currently the use of other methods requires calling loadCSV file; so, either
*         a) throw an error message if loadCSVFile() isn't called first or 
*         b) combine ModulesDatabase() with loadCSVFile()
*
*   TODO: when doing threading, warn user if he tries to quit the app while a tread hasn't finished writing to a file
*   TODO: decide on uniform variable names (e.g. newModuleTitle vs newTitle)
*   TODO: shall we return Module[] arrays instead of integer arrays? also, how about renaming findModuleRowByTitle to findByTitle, ModulesDatabase can then be renamed into ModulesDatabase
*   TODO: throw an error if a database w/o a file is queried?
*/

package module_database;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author sevabaskin
 */
class ModulesDatabase {
    private File databaseFile;
    private Module[] db;

    ModulesDatabase() {    }

    // TODO: normalize all queries by upcasing; normalize results as well?
    synchronized void loadCSVFile(String databaseFileDirectory) throws FileNotFoundException, IOException, InvalidModuleFormatException, EmptyValueException {
        // create an array as large as there're numbers in a csv file
        this.db = new Module[ModuleAppHelper.linesInAFile(databaseFileDirectory)];
        this.databaseFile = new File(databaseFileDirectory);
        BufferedReader reader = new BufferedReader(new FileReader(databaseFile));
        String line;
        int i = 0;
        Pattern csvRegex = Pattern.compile("\"(.*?)\"");
        while ((line = reader.readLine()) != null) {
            // #OPTIMIZE
            Matcher csvMatcher = csvRegex.matcher(line);
            csvMatcher.find();
            String newCode = csvMatcher.group(1);
            csvMatcher.find();
            String newTitle = csvMatcher.group(1);
            csvMatcher.find();
            String newLeaderName = csvMatcher.group(1);
            csvMatcher.find();
            String newLeaderEmail = csvMatcher.group(1);

            this.db[i] = new Module(newCode, newTitle, newLeaderName, newLeaderEmail);
            i++;
        }
    }


    int findModuleRowByCode(String moduleCodeQuery) {
        int resultRow = -1;
        for (int i=0; i < this.db.length ; i++) {
            if (getModule(i).getCode().equals(moduleCodeQuery)) {
                resultRow = i;
                break;
            }
        }
        return resultRow;
    }

    // JavaDoc: Describe how strings are used to generte dynamyc-length arrays
    int[] findModuleRowsByYear(String moduleYearQuery) {
        // if (moduleYearQuery.length() == 0) return new int[]{-1};
        Pattern moduleYearRegex = Pattern.compile("(?<=^...)(1|2|3|M|m)");
        String resultRows = ""; // if nothing's found, assign an empty array.
        for (int i=0; i < this.db.length ; i++) {
            Matcher moduleYearMatcher = moduleYearRegex.matcher(getModule(i).getCode());
            moduleYearMatcher.find();
            String candidateResult = moduleYearMatcher.group();
            if (candidateResult.equals(moduleYearQuery))
                resultRows = resultRows + i + ",";
        }
        return ModuleAppHelper.convertStringToIntArray(resultRows);
    }


    int[] findModuleRowsByLeader(String method, String query) {
        // if (moduleLeaderEmailQuery.length() == 0) return new int[]{-1};
        // if method is smth else, throw some sort of exception? or turn methods into enums
        String resultRows = ""; // if nothing's found, assign an empty array.
        Pattern pattern = Pattern.compile(query);
        Matcher moduleLeaderMatcher = null;
        for (int i=0; i < this.db.length ; i++) {
            if (method.equals("name"))
                moduleLeaderMatcher = pattern.matcher(getModule(i).getLeaderName());
            if (method.equals("email"))
                moduleLeaderMatcher = pattern.matcher(getModule(i).getLeaderEmail());
            if (moduleLeaderMatcher.lookingAt())
                resultRows = resultRows + i + ",";
        }
        return ModuleAppHelper.convertStringToIntArray(resultRows);
    }

    synchronized void updateModule(final int moduleRow, String newModuleCode, String newModuleTitle, String newModuleLeaderName, String newModuleLeaderEmail) throws InvalidModuleFormatException, EmptyValueException, DuplicateModuleException {
        // TODO: extract into to private methods updateDatabaseArray & updateDatabaseCSV, then call both in here after validating values

        File tempDatabaseFile = new File("temp_" + this.databaseFile.getName());
        // update the database array
        // FIXME: shall we perform this after updating CSV file in case of errors? But errors should be caught.

        // If not empty & not the same as existing value
        Module moduleToUpdate = getModule(moduleRow);

        if (!newModuleCode.equals("") && !newModuleCode.equals(moduleToUpdate.getCode()))
            verifyNotDuplicate(newModuleCode);
            moduleToUpdate.setCode(newModuleCode);
        if (!newModuleTitle.equals(""))
            moduleToUpdate.setTitle(newModuleTitle);
        if (!newModuleLeaderName.equals(""))
            moduleToUpdate.setLeaderName(newModuleLeaderName);
        if (!newModuleLeaderEmail.equals(""))
            moduleToUpdate.setLeaderEmail(newModuleLeaderEmail);

        // update the CSV file
        // TODO: Move to thread
        final String substituteLine = "\""+newModuleCode+"\",\""+ newModuleTitle +"\",\""+ newModuleLeaderName +"\",\""+newModuleLeaderEmail+"\"";
        
        new Thread() {
            public void run() {
                ModuleAppHelper.modifyLineInAFile(databaseFile, moduleRow, "update", substituteLine);
                // Catch InterruptedException? "File-writing has been inturrupted. The database csv file may be corrupt."
                // No. Just ship, handle later.
            }
        }.start();

        // ModuleAppHelper.modifyLineInAFile(this.databaseFile, moduleRow, "update", substituteLine);

    }

    synchronized void deleteModule(final int moduleRow) {
        // TODO: throw an exception if nothing's found?
        // delete from the database Array first
        Module[] newDB = new Module[this.db.length-1];
        int j = 0;
        for (int i=0; i< newDB.length; i++) {
            if (i == moduleRow) j++;
            newDB[i] = this.db[j]; 
            j++;
        }
        this.db = newDB;

       // Delete from CSV:
       // TODO: Move to thread
        new Thread() {
            public void run() {
                ModuleAppHelper.modifyLineInAFile(databaseFile, moduleRow, "delete", null);
            }
        }.start();
        // ModuleAppHelper.modifyLineInAFile(databaseFile, moduleRow, "delete", null);
    }

    synchronized void createModule(String newModuleCode, String newModuleTitle, String newModuleLeaderName, String newModuleLeaderEmail) throws DuplicateModuleException, InvalidModuleFormatException, EmptyValueException {
        verifyNotDuplicate(newModuleCode);
        // Expand database:
        Module[] newDB = new Module[this.db.length+1];         // add to databaseArray:
        for (int i=0; i<this.db.length; i++) {                 // Copy old database into the new
            newDB[i] = this.db[i];
        }
        newDB[newDB.length-1] = new Module(newModuleCode, newModuleTitle, newModuleLeaderName, newModuleLeaderEmail);         // Add new module to the new database
        this.db = newDB;         // reasign new database to the global database

        final String line = "\""+newModuleCode+"\",\""+ newModuleTitle +"\",\""+newModuleLeaderName+"\",\""+newModuleLeaderEmail+"\"";
        
        new Thread() {
            public void run() {
                ModuleAppHelper.appendLineToFile(databaseFile, line); // append line to the databaseCSVfile
            }
        }.start();
    }


    // Getters
    Module[] getDb() { return this.db; }
    Module getModule(int moduleRow) { return this.db[moduleRow]; }
 
    // Helpers
    void verifyNotDuplicate(String moduleCode) throws DuplicateModuleException {
        for (Module module : this.db) {
            if (module.getCode().equals(moduleCode)){
                throw new DuplicateModuleException("A module with the same module code already exists, you cannot add a duplicate.");
            }
        }
    }

}