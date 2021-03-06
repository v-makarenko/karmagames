package md.task.service;

import com.infomatiq.jsi.Point;
import com.infomatiq.jsi.Rectangle;
import com.infomatiq.jsi.SpatialIndex;
import com.infomatiq.jsi.rtree.RTree;
import gnu.trove.TIntProcedure;
import md.task.AppConsts;
import md.task.domain.Label;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

@Service
public class GeoService {
    private SpatialIndex si;
    private List<Float> errors = new ArrayList<>(10_000_000);
    private NavigableMap<Long, Label> labels = new TreeMap<>();
    private List<Rectangle> rectangles = new ArrayList<>(10_000_000);

    @PostConstruct
    public void preloadData() throws IOException {
        Scanner geoScanner = new Scanner(Paths.get("geo.csv"));
        Scanner lblScanner = new Scanner(Paths.get("lbl.csv"));

        si = new RTree();
        si.init(null);

        int id = 0;
        while (geoScanner.hasNext()) {
            String[] lineSplit = geoScanner.nextLine().split(",");
            float error = Float.parseFloat(lineSplit[0]);
            float x = Float.parseFloat(lineSplit[1]);
            float y = Float.parseFloat(lineSplit[2]);
            Rectangle rectangle = new Rectangle(x - AppConsts.W, y - AppConsts.H,
                    x + AppConsts.W, y + AppConsts.H);
            si.add(rectangle, id++);
            rectangles.add(rectangle);
            errors.add(error);
        }
        System.out.println("Loaded geo");
        while (lblScanner.hasNext()) {
            String[] lineSplit = lblScanner.nextLine().split(",");
            long userId = Long.parseLong(lineSplit[0]);
            float x = Float.parseFloat(lineSplit[1]);
            float y = Float.parseFloat(lineSplit[2]);
            labels.put(userId, new Label(userId, x, y));
        }
        System.out.println("Loaded lables");
    }

    public boolean findIfNear(Long userId, Float lat, Float lon) {
        Label label = labels.get(userId);
        class FindIfCloseProcedure implements TIntProcedure {
            public float getError() {
                return error;
            }

            private float error;

            @Override
            public boolean execute(int rectIndex) {
                error = errors.get(rectIndex);
                return true;
            }
        }

        float distance = label.findDistance(lat, lon);
        FindIfCloseProcedure myProc = new FindIfCloseProcedure();
        si.nearest(new Point(lat, lon), myProc
                , Float.MAX_VALUE);
        return myProc.getError() > distance;
    }

    public long labelsCount(Float lat, Float lon) {
        class FindRectProc implements TIntProcedure {
            public int getId() {
                return id;
            }

            private int id;

            @Override
            public boolean execute(int rectIndex) {
                id = rectIndex;
                return true;
            }
        }

        FindRectProc myProc = new FindRectProc();
        si.nearest(new Point(lat, lon), myProc
                , Float.MAX_VALUE);

        Rectangle rect = rectangles.get(myProc.getId());
        return labels.values().stream().parallel().filter(lbl -> rect.distance(new Point(lbl.lat, lbl.lon))==0).count();
    }

}

