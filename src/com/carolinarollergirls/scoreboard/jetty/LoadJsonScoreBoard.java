package com.carolinarollergirls.scoreboard.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.fasterxml.jackson.jr.ob.JSON;

import com.carolinarollergirls.scoreboard.core.interfaces.ScoreBoard;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProvider.Flag;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProvider.Source;
import com.carolinarollergirls.scoreboard.json.ScoreBoardJSONSetter;
import com.carolinarollergirls.scoreboard.utils.StatsbookImporter;

/**
 * Servlet to handle requests from web browser to upload a JSON or XLSX file.
 */
public class LoadJsonScoreBoard extends HttpServlet {
    public LoadJsonScoreBoard(ScoreBoard sb) {
        this.scoreBoard = sb;
        sbImporter = new StatsbookImporter(sb);
    }

    /**
     * Handle recieving a JSON or XLSX file from a web browser.
     * <p> This handles one or three types of files, depending on the path. </p>
     * <ul>
     *  <li>/JSON       - A JSON file containing a scoreboard state (State can be 
     * *                  a saved game, team, ruleset, of operator key bindings).</li>
     *  <li>/xlsx       - An XLSX file containing Statsbook data (i.e. a prepared game).</li>
     *  <li>/blank_xlsx - An XLSX file that is a blank Statsbook template.</li>
     * </ul>
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        if (scoreBoard.getClients().getDevice(request.getSession().getId()).mayWrite()) {
            scoreBoard.getClients().getDevice(request.getSession().getId()).write();
            scoreBoard.runInBatch(new Runnable() {
                @Override
                public void run() {
                    scoreBoard.set(ScoreBoard.IMPORTS_IN_PROGRESS, 1, Flag.CHANGE);
                }
            });
            try {
                if (!ServletFileUpload.isMultipartContent(request)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                ServletFileUpload sfU = new ServletFileUpload();
                FileItemIterator items = sfU.getItemIterator(request);
                while (items.hasNext()) {
                    FileItemStream item = items.next();
                    if (!item.isFormField()) {
                        if (request.getPathInfo().equalsIgnoreCase("/JSON")) {
                            runningImports.incrementAndGet();
                            InputStream stream = item.openStream();
                            Map<String, Object> map = JSON.std.mapFrom(stream);
                            stream.close();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> state = (Map<String, Object>) map.get("state");
                            ScoreBoardJSONSetter.updateToCurrentVersion(state);
                            scoreBoard.runInBatch(new Runnable() {
                                @Override
                                public void run() {
                                    ScoreBoardJSONSetter.set(scoreBoard, state, Source.JSON);
                                }
                            });
                            runningImports.decrementAndGet();
                            response.setContentType("text/plain");
                            response.setStatus(HttpServletResponse.SC_OK);

                            synchronized (runningImports) {
                                if (runningImports.get() == 0) { scoreBoard.cleanupAliases(); }
                            }
                        } else if (request.getPathInfo().equalsIgnoreCase("/xlsx")) {
                            sbImporter.read(item.openStream());
                        } else if (request.getPathInfo().equalsIgnoreCase("/blank_xlsx")) {
                            Path outputPath = Paths.get("blank_statsbook.xlsx");
                            Files.copy(item.openStream(), outputPath, StandardCopyOption.REPLACE_EXISTING);
                            scoreBoard.runInBatch(new Runnable() {
                                @Override
                                public void run() {
                                    scoreBoard.getSettings().set(ScoreBoard.SETTING_STATSBOOK_INPUT,
                                                                 outputPath.toString());
                                }
                            });
                        }
                        return;
                    }
                }
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No File uploaded");
            } catch (FileUploadException fuE) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, fuE.getMessage());
            } catch (IOException iE) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Error Reading File: " + iE.getMessage());
            } finally {
                scoreBoard.runInBatch(new Runnable() {
                    @Override
                    public void run() {
                        scoreBoard.set(ScoreBoard.IMPORTS_IN_PROGRESS, -1, Flag.CHANGE);
                    }
                });
            }
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No write access");
        }
    }

    protected final ScoreBoard scoreBoard;
    protected final StatsbookImporter sbImporter;

    protected static AtomicInteger runningImports = new AtomicInteger(0);
}
