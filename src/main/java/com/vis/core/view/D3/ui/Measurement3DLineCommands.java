package com.vis.core.view.D3.ui;

import java.util.List;
import org.joml.Vector3f;

public class Measurement3DLineCommands {

    public static class AddPointCommand implements UndoManager.Command {
        private final List<Vector3f> renderPts;
        private final List<Vector3f> mmPts;
        private final Vector3f renderPt;
        private final Vector3f mmPt;

        public AddPointCommand(List<Vector3f> renderPts, List<Vector3f> mmPts,
                               Vector3f renderPt, Vector3f mmPt) {
            this.renderPts = renderPts;
            this.mmPts     = mmPts;
            this.renderPt  = new Vector3f(renderPt);
            this.mmPt      = new Vector3f(mmPt);
        }

        @Override public void execute() { renderPts.add(new Vector3f(renderPt)); mmPts.add(new Vector3f(mmPt)); }
        @Override public void undo()    {
            if (!renderPts.isEmpty()) renderPts.remove(renderPts.size() - 1);
            if (!mmPts.isEmpty())     mmPts.remove(mmPts.size() - 1);
        }
    }

    public static class ClearCommand implements UndoManager.Command {
        private final List<Vector3f> renderPts;
        private final List<Vector3f> mmPts;
        private final List<Vector3f> savedRender;
        private final List<Vector3f> savedMm;

        public ClearCommand(List<Vector3f> renderPts, List<Vector3f> mmPts) {
            this.renderPts   = renderPts;
            this.mmPts       = mmPts;
            this.savedRender = new java.util.ArrayList<>(renderPts);
            this.savedMm     = new java.util.ArrayList<>(mmPts);
        }

        @Override public void execute() { renderPts.clear(); mmPts.clear(); }
        @Override public void undo()    { renderPts.addAll(savedRender); mmPts.addAll(savedMm); }
    }
}
