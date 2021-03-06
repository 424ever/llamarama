package at.jojokobi.llamarama.tiles;

import at.jojokobi.donatengine.objects.Tile;
import at.jojokobi.donatengine.rendering.Image2DModel;
import at.jojokobi.donatengine.rendering.RenderModel;
import at.jojokobi.llamarama.LlamaramaApplication;
import javafx.scene.image.Image;

public class StompTile extends Tile {
	
	public static final Image STOMP_IMAGE = new Image(StompTile.class.getResourceAsStream(LlamaramaApplication.IMAGE_PATH + "tiles/stomp.png"));
	public static final RenderModel STOMP_MODEL = new Image2DModel(STOMP_IMAGE);
	
	public StompTile(double x, double y, double z, String area) {
		super(x, y, z, area, 32, 32, 32, STOMP_MODEL);
		setSolid(true);
		setNeedsUpdate(false);
	}
	
	public StompTile() {
		this(0, 0, 0, "");
	}

}
