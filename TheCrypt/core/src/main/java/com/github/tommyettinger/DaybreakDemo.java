/*
 * Copyright (c) 2022-2023 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tommyettinger;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Colors;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScalingViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.tommyettinger.cringe.EncryptedFileHandle;
import com.github.tommyettinger.cringe.RandomDistinct64;
import com.github.tommyettinger.digital.BitConversion;
import com.github.tommyettinger.digital.TrigTools;
import com.github.tommyettinger.ds.IntObjectMap;
import com.github.tommyettinger.ds.ObjectDeque;
import com.github.tommyettinger.ds.ObjectList;
import com.github.tommyettinger.random.ChopRandom;
import com.github.yellowstonegames.core.DescriptiveColor;
import com.github.yellowstonegames.core.FullPalette;
import com.github.yellowstonegames.grid.Coord;
import com.github.yellowstonegames.grid.CoordObjectOrderedMap;
import com.github.yellowstonegames.grid.Direction;
import com.github.yellowstonegames.grid.LineTools;
import com.github.yellowstonegames.grid.Measurement;
import com.github.yellowstonegames.grid.Radiance;
import com.github.yellowstonegames.grid.Region;
import com.github.yellowstonegames.grid.VisionFramework;
import com.github.yellowstonegames.path.DijkstraMap;
import com.github.yellowstonegames.place.DungeonProcessor;
import com.github.yellowstonegames.smooth.AnimatedGlidingSprite;
import com.github.yellowstonegames.smooth.CoordGlider;
import com.github.yellowstonegames.smooth.Director;
import com.github.yellowstonegames.smooth.VectorSequenceGlider;
import com.github.yellowstonegames.text.Language;

import static com.badlogic.gdx.Gdx.input;
import static com.badlogic.gdx.Input.Keys.*;
import static com.github.tommyettinger.digital.BitConversion.intBitsToFloat;
import static com.github.yellowstonegames.core.DescriptiveColor.*;

public class DaybreakDemo extends ApplicationAdapter {
    private static final float DURATION = 0.375f;
    private long startTime, lastMove;

    private enum Phase {WAIT, PLAYER_ANIM, MONSTER_ANIM}

    private SpriteBatch batch;
    private Phase phase = Phase.WAIT;

    // random number generator; this one is more efficient on GWT, but less-so on desktop.
    private ChopRandom rng;

    private final RandomDistinct64 unseededRandom = new RandomDistinct64();

    public long seed = 0L;

    // Stores all images we use here efficiently, as well as the font image
    private TextureAtlas atlas;
    // This maps chars, such as '#', to specific images, such as a pillar.
    private IntObjectMap<TextureAtlas.AtlasRegion> charMapping;

    /**
     * The dungeon map using only {@code '#'} for walls and {@code '.'} for floors.
     */
    private char[][] barePlaceMap;

    /**
     * All floors that the player can walk on.
     */
    private Region floors;

    /**
     * Handles field of view calculations as they change when the player moves around; also, lighting with colors.
     */
    private final VisionFramework vision = new VisionFramework();
    /**
     * The 2D position of the player (the moving character who the FOV centers upon).
     */
    private Coord player;

    private final Coord[] playerArray = new Coord[1];

    private final Vector2 pos = new Vector2();

    /**
     * In number of cells
     */
    public static final int shownWidth = 32;
    /**
     * In number of cells
     */
    public static final int shownHeight = 24;

    /**
     * In number of cells
     */
    public static final int placeWidth = shownWidth * 2;
    /**
     * In number of cells
     */
    public static final int placeHeight = shownHeight * 2;

    /**
     * The pixel width of a cell
     */
    public static final int cellWidth = 32;
    /**
     * The pixel height of a cell
     */
    public static final int cellHeight = 32;

    private boolean onGrid(int screenX, int screenY) {
        return screenX >= 0 && screenX < placeWidth && screenY >= 0 && screenY < placeHeight;
    }

    private BitmapFont font;
    private Viewport mainViewport;
    private Camera camera;

    private CoordObjectOrderedMap<AnimatedGlidingSprite> monsters;
    private AnimatedGlidingSprite playerSprite;
    private Director<AnimatedGlidingSprite> playerDirector;
    private Director<Coord> monsterDirector, directorSmall;
    private DijkstraMap getToPlayer, playerToCursor;
    private Coord cursor;
    private ObjectDeque<Coord> toCursor;
    private ObjectDeque<Coord> awaitedMoves;
    private ObjectDeque<Coord> nextMovePositions;
    private String lang;
    private TextureAtlas.AtlasRegion solid;
    private int health = 9;

    /**
     * Used to tint the player for a round when they suffer damage. Very dark red, slightly dull.
     */
    private static final int OKLAB_BLOOD = DescriptiveColor.describeOklab("darkest brick");
    /**
     * Used as the color for remembered cells that can't be currently seen.
     * Gray with about 30% lightness; fully opaque.
     */
    private static final int OKLAB_MEMORY = 0xFF808060;

    public static final float NEUTRAL_PACKED = BitConversion.intBitsToFloat(0xFEFFFFFF & DescriptiveColor.WHITE);

    public DaybreakDemo() {
        this(1L);
    }

    public DaybreakDemo(long seed) {
        this.seed = seed;
        for(Color c : Colors.getColors().values()) {
            final int f = DescriptiveColor.fromRGBA(c.r, c.g, c.b, c.a);
            c.set(channelL(f), channelA(f), channelB(f), c.a);
        }

    }

    /**
     * Just the parts of create() that can be called again if the game is reloaded.
     */
    public void restart() {
        restart(TimeUtils.millis() ^ System.identityHashCode(this));
    }

    /**
     * Just the parts of create() that can be called again if the game is reloaded.
     */
    public void restart(long seed) {
        health = 9;
        phase = Phase.WAIT;
        toCursor.clear();
        awaitedMoves.clear();
        nextMovePositions.clear();
        // Starting time for the game; other times are measured relative to this so that they aren't huge numbers.
        startTime = TimeUtils.millis();
        lastMove = startTime;
        // We just need to have a random number generator.
        // This is seeded the same every time.
        rng.setSeed(12345);
        // Using this would give a different dungeon every time.
//        rng = new ChopRandom(startTime);

        //This uses the seeded RNG we made earlier to build a procedural dungeon using a method that takes rectangular
        //sections of pre-drawn dungeon and drops them into place in a tiling pattern. It makes good winding dungeons
        //with rooms by default, but in the later call to dungeonGen.generate(), you can use a TilesetType such as
        //TilesetType.ROUND_ROOMS_DIAGONAL_CORRIDORS or TilesetType.CAVES_LIMIT_CONNECTIVITY to change the sections that
        //this will use, or just pass in a full 2D char array produced from some other generator, such as
        //SerpentMapGenerator, OrganicMapGenerator, or DenseRoomMapGenerator.
        DungeonProcessor dungeonGen = new DungeonProcessor(placeWidth, placeHeight, rng);
        //this next line randomly adds water to the dungeon in pools.
        dungeonGen.addWater(DungeonProcessor.ALL, 12);
        //this next line makes 10% of valid door positions into complete doors.
        dungeonGen.addDoors(10, true);
        //this next line randomly adds water to the cave parts of the dungeon in patches.
        dungeonGen.addGrass(DungeonProcessor.ALL, 10);
        //some boulders make the map a little more tactically interesting, and show how the FOV works.
        dungeonGen.addBoulders(DungeonProcessor.ALL, 5);
        //When we draw, we may want to use a nicer representation of walls. DungeonUtility has lots of useful methods
        //for modifying char[][] dungeon grids, and this one takes each '#' and replaces it with a box-drawing char.
        //The end result looks something like this, for a smaller 60x30 map:
        //
        // ┌───┐┌──────┬──────┐┌──┬─────┐   ┌──┐    ┌──────────┬─────┐
        // │...││......│......└┘..│.....│   │..├───┐│..........│.....└┐
        // │...││......│..........├──┐..├───┤..│...└┴────......├┐.....│
        // │...││.................│┌─┘..│...│..│...............││.....│
        // │...││...........┌─────┘│....│...│..│...........┌───┴┴───..│
        // │...│└─┐....┌───┬┘      │........│..│......─────┤..........│
        // │...└─┐│....│...│       │.......................│..........│
        // │.....││........└─┐     │....│..................│.....┌────┘
        // │.....││..........│     │....├─┬───────┬─┐......│.....│
        // └┬──..└┼───┐......│   ┌─┴─..┌┘ │.......│ │.....┌┴──┐..│
        //  │.....│  ┌┴─..───┴───┘.....└┐ │.......│┌┘.....└─┐ │..│
        //  │.....└──┘..................└─┤.......││........│ │..│
        //  │.............................│.......├┘........│ │..│
        //  │.............┌──────┐........│.......│...─┐....│ │..│
        //  │...........┌─┘      └──┐.....│..─────┘....│....│ │..│
        // ┌┴─────......└─┐      ┌──┘..................│..──┴─┘..└─┐
        // │..............└──────┘.....................│...........│
        // │............................┌─┐.......│....│...........│
        // │..│..│..┌┐..................│ │.......├────┤..──┬───┐..│
        // │..│..│..│└┬──..─┬───┐......┌┘ └┐.....┌┘┌───┤....│   │..│
        // │..├──┤..│ │.....│   │......├───┘.....│ │...│....│┌──┘..└──┐
        // │..│┌─┘..└┐└┬─..─┤   │......│.........└─┘...│....││........│
        // │..││.....│ │....│   │......│...............│....││........│
        // │..││.....│ │....│   │......│..┌──┐.........├────┘│..│.....│
        // ├──┴┤...│.└─┴─..┌┘   └┐....┌┤..│  │.....│...└─────┘..│.....│
        // │...│...│.......└─────┴─..─┴┘..├──┘.....│............└─────┤
        // │...│...│......................│........│..................│
        // │.......├───┐..................│.......┌┤.......┌─┐........│
        // │.......│   └──┐..┌────┐..┌────┤..┌────┘│.......│ │..┌──┐..│
        // └───────┘      └──┘    └──┘    └──┘     └───────┘ └──┘  └──┘
        //this is also good to compare against if the map looks incorrect, and you need an example of a correct map when
        //no parameters are given to generate().
        char[][] linePlaceMap = LineTools.hashesToLines(dungeonGen.generate(), true);
        //linePlaceMap is given the dungeon with any decorations we specified. (Here, we didn't, unless you chose to add
        //water to the dungeon. In that case, linePlaceMap will have different contents than barePlaceMap, next.)
        //getBarePlaceGrid() provides the simplest view of the generated dungeon -- '#' for walls, '.' for floors.
        barePlaceMap = dungeonGen.getBarePlaceGrid();

        // here, we need to get a random floor cell to place the player upon, without the possibility of putting him
        // inside a wall. There are a few ways to do this in SquidSquad. The most straightforward way is to randomly
        // choose x and y positions until a floor is found, but particularly on dungeons with few floor cells, this can
        // have serious problems -- if it takes too long to find a floor cell, either it needs to be able to figure out
        // that random choice isn't working and instead choose the first it finds in simple iteration, or potentially
        // keep trying forever on an all-wall map. There are better ways! These involve using a kind of specific storage
        // for points or regions, getting that to store only floors, and finding a random cell from that collection of
        // floors. SquidSquad provides the Region class to handle on-or-off regions of positions in a larger grid. It's
        // relatively efficient to get a random point from a Region, especially on maps with few valid points to choose;
        // there are lots of other features Region has that make it a good choice for lots of location-related code.

        // Here we fill a Region; it stores the cells that contain a floor, the '.' char, as "on."
        // Region is a hard-to-explain class, but it's an incredibly useful one for map generation and many other tasks;
        // it stores a region of "on" cells where everything not in that region is considered "off," and can be used as
        // a Collection of Coord points. However, it's more than that! Because of how it is implemented, it can perform
        // bulk operations on as many as 64 points at a time, and can efficiently do things like expanding the "on" area
        // to cover adjacent cells that were "off", retracting the "on" area away from "off" cells to shrink it, getting
        // the surface ("on" cells that are adjacent to "off" cells) or fringe ("off" cells that are adjacent to "on"
        // cells), and generally useful things like picking a random point from all "on" cells. Here, we use a Region to
        // store all floors that the player can walk on, a small rim of cells just beyond the player's vision that
        // blocks pathfinding to areas we can't see a path to, and we also store all cells that we have seen in the past
        // in a Region (in most roguelikes, there would be one of these per dungeon floor).
        floors = floors == null ? new Region(barePlaceMap, '.') : floors.refill(barePlaceMap, '.');
        //player is, here, just a Coord that stores his position. In a real game, you would probably have a class for
        //creatures, and possibly a subclass for the player. The singleRandom() method on Region finds one Coord
        //in that region that is "on," or -1,-1 if there are no such cells. It takes an RNG object as a parameter, and
        //if you gave a seed to the RNG constructor, then the cell this chooses will be reliable for testing. If you
        //don't seed the RNG, any valid cell should be possible.
        player = floors.singleRandom(rng);
        playerSprite = new AnimatedGlidingSprite(new Animation<>(DURATION,
                atlas.findRegions(rng.randomElement(Data.possibleCharacters)), Animation.PlayMode.LOOP), player);
        playerSprite.setSize(1f, 1f);
        playerSprite.setPackedColor(NEUTRAL_PACKED);
        playerDirector = new Director<>(AnimatedGlidingSprite::getLocation, ObjectList.with(playerSprite), 150);
        vision.restart(linePlaceMap, player, 8);
//        vision.lighting.addLight(player, new Radiance(8, FullPalette.COSMIC_LATTE, 0f, 0f));
        vision.lighting.addLight(player, new Radiance(8, FullPalette.COSMIC_LATTE, 0.3f, 0f));
        floors.remove(player);
        int numMonsters = 100;
        monsters = new CoordObjectOrderedMap<>(numMonsters);
        for (int i = 0; i < numMonsters; i++) {
            Coord monPos = floors.singleRandom(rng);
            floors.remove(monPos);
            String enemy = rng.randomElement(Data.possibleEnemies);
            AnimatedGlidingSprite monster =
                    new AnimatedGlidingSprite(new Animation<>(DURATION,
                            atlas.findRegions(enemy), Animation.PlayMode.LOOP), monPos);
            monster.setSize(1f, 1f);
            monsters.put(monPos, monster);
            vision.lighting.addLight(monPos, new Radiance(rng.nextFloat(3f) + 2f,
//                    FullPalette.COLOR_WHEEL_PALETTE_LIGHT[rng.nextInt(FullPalette.COLOR_WHEEL_PALETTE_LIGHT.length)], 0f, 0f));
                    FullPalette.COLOR_WHEEL_PALETTE_MID[unseededRandom.nextInt(FullPalette.COLOR_WHEEL_PALETTE_MID.length)], 0.5f, 0f));
        }
//        monsterDirector = new Director<>((e) -> e.getValue().getLocation(), monsters, 125);
        monsterDirector = new Director<>(c -> monsters.get(c).getLocation(), monsters.order(), 150);
        directorSmall = new Director<>(c -> monsters.get(c).getSmallMotion(), monsters.order(), 300L);
        //This is used to allow clicks or taps to take the player to the desired area.
        //When a path is confirmed by clicking, we draw from this List to find which cell is next to move into.
        //DijkstraMap is the pathfinding swiss-army knife we use here to find a path to the latest cursor position.
        //DijkstraMap.Measurement is an enum that determines the possibility or preference to enter diagonals. Here, the
        //Measurement used is EUCLIDEAN, which allows 8 directions, but will prefer orthogonal moves unless diagonal
        //ones are clearly closer "as the crow flies." Alternatives are MANHATTAN, which means 4-way movement only, no
        //diagonals possible, and CHEBYSHEV, which allows 8 directions of movement at the same cost for all directions.
        playerToCursor = new DijkstraMap(barePlaceMap, Measurement.EUCLIDEAN);
        getToPlayer = new DijkstraMap(barePlaceMap, Measurement.EUCLIDEAN);
        //These next two lines mark the player as something we want paths to go to or from, and get the distances to the
        // player from somewhat-nearby walkable cells in the dungeon.
        playerToCursor.setGoal(player);
        // DijkstraMap.partialScan only finds the distance to get to a cell if that distance is less than some limit,
        // which is 13 here. It also won't try to find distances through an impassable cell, which here is the blockage
        // Region that contains the cells just past the edge of the player's FOV area.
        playerToCursor.partialScan(13, vision.blockage);

        lang = '"' + Language.DEMONIC.sentence(rng, 4, 7,
                new String[]{",", ",", ",", " -"}, new String[]{"...\"", ", heh...\"", ", nyehehe...\"", "!\"", "!\"", "!\"", "!\" *PTOOEY!*",}, 0.2);

    }
    /**
     * Returns a new instance of an Oklab shader for GL2 when no shader is specified.
     */
    public static ShaderProgram createDefaultShader () {
        String vertexShader = "attribute vec4 a_position;\n"
                + "attribute vec4 a_color;\n"
                + "attribute vec2 a_texCoord0;\n"
                + "uniform mat4 u_projTrans;\n"
                + "uniform vec3 u_globalAdd;\n"
                + "uniform vec3 u_globalMul;\n"
                + "varying vec4 v_color;\n"
                + "varying vec2 v_texCoords;\n"
                + "\n"
                + "void main(){\n"
                + "   v_color = a_color;\n"
                + "   v_color.r = (v_color.r - 0.5) * 0.5 * u_globalMul.r + u_globalAdd.r - 0.25;\n"
                + "   v_color.gb = (v_color.gb - 0.5) * u_globalMul.gb + u_globalAdd.gb;\n"
                + "   v_color.a = v_color.a * (255.0/254.0);\n"
                + "   v_texCoords = a_texCoord0;\n"
                + "   gl_Position = u_projTrans * a_position;\n"
                + "}\n";
        String fragmentShaderOklab =
                "#ifdef GL_ES\n" +
                        "#define LOWP lowp\n" +
                        "precision mediump float;\n" +
                        "#else\n" +
                        "#define LOWP \n" +
                        "#endif\n" +
                        "varying vec2 v_texCoords;\n" +
                        "varying LOWP vec4 v_color;\n" +
                        "uniform sampler2D u_texture;\n" +
                        "const vec3 forward = vec3(1.0 / 3.0);\n" +
                        "void main(){\n" +
                        "  vec4 tgt = texture2D( u_texture, v_texCoords );\n" +
                        "  vec3 lab = mat3(+0.2104542553, +1.9779984951, +0.0259040371, +0.7936177850, -2.4285922050, +0.7827717662, -0.0040720468, +0.4505937099, -0.8086757660) *" +
                        "             pow(mat3(0.4121656120, 0.2118591070, 0.0883097947, 0.5362752080, 0.6807189584, 0.2818474174, 0.0514575653, 0.1074065790, 0.6302613616) \n" +
                        "             * (tgt.rgb * tgt.rgb), forward);\n" +
                        "  lab.x = pow(clamp(pow(lab.x, 1.5) + v_color.r, 0.0, 1.0), 0.666666);\n" +
                        "  lab.yz = clamp(lab.yz + v_color.gb * 2.0, -1.0, 1.0);\n" +
                        "  lab = mat3(1.0, 1.0, 1.0, +0.3963377774, -0.1055613458, -0.0894841775, +0.2158037573, -0.0638541728, -1.2914855480) * lab;\n" +
                        "  gl_FragColor = vec4(sqrt(clamp(" +
                        "                 mat3(+4.0767245293, -1.2681437731, -0.0041119885, -3.3072168827, +2.6093323231, -0.7034763098, +0.2307590544, -0.3411344290, +1.7068625689) *\n" +
                        "                 (lab * lab * lab)," +
                        "                 0.0, 1.0)), v_color.a * tgt.a);\n" +
                        "}";

        ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShaderOklab);
        if (!shader.isCompiled()) throw new IllegalArgumentException("Error compiling shader: " + shader.getLog());
        return shader;
    }

    @Override
    public void create() {

        Gdx.app.setLogLevel(Application.LOG_INFO);
        // We need access to a batch to render most things.
        batch = new SpriteBatch(8000, createDefaultShader());

        rng = new ChopRandom(seed);

        mainViewport = new ScalingViewport(Scaling.fill, shownWidth, shownHeight);
        mainViewport.setScreenBounds(0, 0, shownWidth * cellWidth, shownHeight * cellHeight);
        camera = mainViewport.getCamera();
        camera.update();

        //This is used to allow clicks or taps to take the player to the desired area.
        toCursor = new ObjectDeque<>(200);
        //When a path is confirmed by clicking, we draw from this List to find which cell is next to move into.
        awaitedMoves = new ObjectDeque<>(200);

        nextMovePositions = new ObjectDeque<>(200);

        // Stores all images we use here efficiently, as well as the font image
        atlas = new TextureAtlas(new EncryptedFileHandle(Gdx.files.internal("dawnlike/Dawnlike.atlas"), 1L, 22L, 333L, 4444L), new EncryptedFileHandle(Gdx.files.internal("dawnlike"), 1L, 22L, 333L, 4444L));
        font = new BitmapFont(new EncryptedFileHandle(Gdx.files.internal("dawnlike/font.fnt"), 1L, 22L, 333L, 4444L), atlas.findRegion("font"));
        font.setUseIntegerPositions(false);
        font.getData().setScale(2f / cellWidth, 2f / cellHeight);
        font.getData().markupEnabled = true;

        vision.rememberedColor = OKLAB_MEMORY;

//        Pixmap pCursor = new Pixmap(cellWidth, cellHeight, Pixmap.Format.RGBA8888);
//        Pixmap pAtlas = new Pixmap(Gdx.files.classpath("dawnlike/Dawnlike.png"));
//        String[] cursorNames = {"broadsword", "dwarvish spear", "javelin", "vulgar polearm", "pole cleaver", "quarterstaff"};
//        TextureAtlas.AtlasRegion pointer = atlas.findRegion(cursorNames[(int) (TimeUtils.millis() & 0xFFFFF) % cursorNames.length]);
//        pCursor.drawPixmap(pAtlas, pointer.getRegionX(), pointer.getRegionY(), 16, 16, 0, 0, cellWidth, cellHeight);
//        Gdx.graphics.setCursor(Gdx.graphics.newCursor(pCursor, 1, 1));
//        pAtlas.dispose();
//        pCursor.dispose();

        solid = atlas.findRegion("pixel");
        charMapping = new IntObjectMap<>(64);
        charMapping.put('.', atlas.findRegion("day tile floor c"));
        charMapping.put(',', atlas.findRegion("brick clear pool center"));
        charMapping.put('~', atlas.findRegion("brick murky pool center"));
        charMapping.put('"', atlas.findRegion("dusk grass floor c"));
        charMapping.put('#', atlas.findRegion("lit brick wall center"));
        charMapping.put('+', atlas.findRegion("closed wooden door front")); //front
        charMapping.put('/', atlas.findRegion("open wooden door side")); //side
        charMapping.put('┌', atlas.findRegion("lit brick wall right down"));
        charMapping.put('└', atlas.findRegion("lit brick wall right up"));
        charMapping.put('┴', atlas.findRegion("lit brick wall left right up"));
        charMapping.put('┬', atlas.findRegion("lit brick wall left right down"));
        charMapping.put('─', atlas.findRegion("lit brick wall left right"));
        charMapping.put('│', atlas.findRegion("lit brick wall up down"));
        charMapping.put('├', atlas.findRegion("lit brick wall right up down"));
        charMapping.put('┼', atlas.findRegion("lit brick wall left right up down"));
        charMapping.put('┤', atlas.findRegion("lit brick wall left up down"));
        charMapping.put('┘', atlas.findRegion("lit brick wall left up"));
        charMapping.put('┐', atlas.findRegion("lit brick wall left down"));

        charMapping.put(' ', atlas.findRegion("lit brick wall up down"));
        charMapping.put('1', atlas.findRegion("red liquid drizzle"));
        charMapping.put('2', atlas.findRegion("red liquid spatter"));
        charMapping.put('s', atlas.findRegion("little shine", 1));

        //Coord is the type we use as a general 2D point, usually in a dungeon.
        //Because we know dungeons won't be incredibly huge, Coord performs best for x and y values less than 256, but
        // by default it can also handle some negative x and y values (-3 is the lowest it can efficiently store). You
        // can call Coord.expandPool() or Coord.expandPoolTo() if you need larger maps to be just as fast.
        cursor = Coord.get(-1, -1);

        restart(seed);

        //+1 is up on the screen
        //-1 is down on the screen
        // if the user clicks and mouseMoved hasn't already assigned a path to toCursor, then we call mouseMoved
        // ourselves and copy toCursor over to awaitedMoves.
        // causes the path to the mouse position to become highlighted (toCursor contains a list of Coords that
        // receive highlighting). Uses DijkstraMap.findPathPreScanned() to find the path, which is rather fast.
        // we also need to check if screenX or screenY is the same cell.
        // This uses DijkstraMap.findPathPreScannned() to get a path as a List of Coord from the current
        // player position to the position the user clicked on. The "PreScanned" part is an optimization
        // that's special to DijkstraMap; because the part of the map that is viable to move into has
        // already been fully analyzed by the DijkstraMap.partialScan() method at the start of the
        // program, and re-calculated whenever the player moves, we only need to do a fraction of the
        // work to find the best path with that info.
        // findPathPreScanned includes the current cell (goal) by default, which is helpful when
        // you're finding a path to a monster or loot, and want to bump into it, but here can be
        // confusing because you would "move into yourself" as your first move without this.
        InputProcessor input = new InputAdapter() {
            @Override
            public boolean keyUp(int keycode) {
                switch (keycode) {
                    case F:
                        // this probably isn't needed currently, since the FPS is shown on-screen.
                        // it could be useful in the future.
                        System.out.println(Gdx.graphics.getFramesPerSecond());
                        break;
                    case ESCAPE:
                        Gdx.app.exit();
                        break;
                }
                return true;
            }

            // if the user clicks and mouseMoved hasn't already assigned a path to toCursor, then we call mouseMoved
            // ourselves and copy toCursor over to awaitedMoves.
            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                pos.set(screenX, screenY);
                mainViewport.unproject(pos);
                if (onGrid(MathUtils.floor(pos.x), MathUtils.floor(pos.y))) {
                    mouseMoved(screenX, screenY);
                    awaitedMoves.addAll(toCursor);
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return mouseMoved(screenX, screenY);
            }

            // causes the path to the mouse position to become highlighted (toCursor contains a list of Coords that
            // receive highlighting). Uses DijkstraMap.findPathPreScanned() to find the path, which is rather fast.
            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                if (!awaitedMoves.isEmpty())
                    return false;
                pos.set(screenX, screenY);
                mainViewport.unproject(pos);
                if (onGrid(screenX = MathUtils.floor(pos.x), screenY = MathUtils.floor(pos.y))) {
                    // we also need to check if screenX or screenY is the same cell.
                    if (cursor.x == screenX && cursor.y == screenY) {
                        return false;
                    }
                    cursor = Coord.get(screenX, screenY);
                    // This uses DijkstraMap.findPathPreScannned() to get a path as a List of Coord from the current
                    // player position to the position the user clicked on. The "PreScanned" part is an optimization
                    // that's special to DijkstraMap; because the part of the map that is viable to move into has
                    // already been fully analyzed by the DijkstraMap.partialScan() method at the start of the
                    // program, and re-calculated whenever the player moves, we only need to do a fraction of the
                    // work to find the best path with that info.
                    toCursor.clear();
                    playerToCursor.findPathPreScanned(toCursor, cursor);
                    // findPathPreScanned includes the current cell (goal) by default, which is helpful when
                    // you're finding a path to a monster or loot, and want to bump into it, but here can be
                    // confusing because you would "move into yourself" as your first move without this.
                    if (!toCursor.isEmpty()) {
                        toCursor.removeFirst();
                    }
                }
                return false;
            }
        };
        Gdx.input.setInputProcessor(input);
    }

    /**
     * Move the player if he isn't bumping into a wall or trying to go off the map somehow.
     * In a fully-fledged game, this would not be organized like this, but this is a one-file demo.
     *
     * @param next where to move
     */
    private void move(Coord next) {
        lastMove = TimeUtils.millis();
        if (health <= 0) return;
        CoordGlider cg = playerSprite.location;
        // this prevents movements from restarting while a slide is already in progress.
        if (cg.getChange() != 0f && cg.getChange() != 1f) return;

        int newX = next.x, newY = next.y;
        playerSprite.setPackedColor(NEUTRAL_PACKED);
        if (newX >= 0 && newY >= 0 && newX < placeWidth && newY < placeHeight
                && barePlaceMap[newX][newY] != '#') {
            // '+' is a door.
            if (vision.prunedPlaceMap[newX][newY] == '+') {
                vision.editSingle(next, '/');
            } else {
                // if a monster was at the position we moved into, and so was successfully removed...
                if (monsters.containsKey(next)) {
                    monsters.remove(next);
                    // remove any light present at the now-dead enemy's location
                    vision.lighting.removeLight(next);
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            if (vision.prunedPlaceMap[newX + x][newY + y] == '.' && rng.nextBoolean())
                                vision.prunedPlaceMap[newX + x][newY + y] = rng.next(2) != 0 ? '1' : '2';
                        }
                    }
                }
                vision.moveViewer(player, next);
                // we can move the player's light now that we know there is no light for an enemy at next.
                vision.lighting.moveLight(player, next);

                playerSprite.location.setStart(player);
                playerSprite.location.setEnd(player = next);
                phase = Phase.PLAYER_ANIM;
                playerDirector.play();
            }
            vision.finishChanges();
            phase = Phase.PLAYER_ANIM;
        }
    }

    private void afterChange() {
        phase = Phase.MONSTER_ANIM;
        // updates our mutable player array in-place, because a Coord like player is immutable.
        playerArray[0] = player;
        int monCount = monsters.size();
        // handle monster turns
        float[][] lightLevels = vision.lighting.fovResult;
        for (int ci = 0; ci < monCount; ci++) {
            Coord pos = monsters.keyAt(ci);
            AnimatedGlidingSprite mon = monsters.getAt(ci);
            if (mon == null) continue;
            // monster values are used to store their aggression, 1 for actively stalking the player, 0 for not.
            if (lightLevels[pos.x][pos.y] > 0.01) {
                // the player's position is set as a goal by findPath(), later.
                getToPlayer.clearGoals();
                // clear the buffer, we fill it next
                nextMovePositions.clear();
                // this gets the path from pos (the monster's starting position) to the player, and stores it in
                // nextMovePositions. it only stores one cell of movement, but it looks ahead up to 7 cells.
                // The keySet() from monsters is interesting here. it contains the current monster, but DijkstraMap
                // ignores the starting cell's blocking-or-not status, so that isn't an issue. the keyset is cached in
                // the CoordObjectOrderedMap, so it doesn't constantly allocate new sets (don't do this with a HashMap).
                // again to reduce allocations, the target position (and there could be more than one in many games) is
                // stored in a one-element array that gets modified, instead of using a new varargs every time (which
                // silently creates an array each time it is called).
                getToPlayer.findPath(nextMovePositions, 1, 7, monsters.keySet(), null, pos, playerArray);
                if (nextMovePositions.notEmpty()) {
                    Coord tmp = nextMovePositions.get(0);
                    if (tmp == null) continue;
                    // if we would move into the player, instead damage the player and animate a bump motion.
                    if (tmp.x == player.x && tmp.y == player.y) {
                        playerSprite.setPackedColor(intBitsToFloat(0xFEFFFFFF & OKLAB_BLOOD));
                        health--;
                        VectorSequenceGlider small = VectorSequenceGlider.BUMPS.getOrDefault(pos.toGoTo(player), null);
                        if (small != null) {
                            small = small.copy();
                            small.setCompleteRunner(() -> mon.setSmallMotion(null));
                        }
                        mon.setSmallMotion(small);
                        directorSmall.play();

                    }
                    // otherwise, make the monster start moving from its current position to its next one.
                    else {
                        mon.location.setStart(pos);
                        mon.location.setEnd(tmp);
                        // this changes the key from pos to tmp without affecting its value.
                        monsters.alter(pos, tmp);
                        vision.lighting.moveLight(pos, tmp);
                    }
                }
            }
        }
        monsterDirector.play();
    }


    /**
     * Draws the map, applies any highlighting for the path to the cursor, and then draws the player.
     */
    public void putMap() {
        float change = (float) Math.min(Math.max(TimeUtils.timeSinceMillis(lastMove) * 4.0, 0.0), 1000.0);
        vision.update(change);
        final float time = TimeUtils.timeSinceMillis(startTime) * 0.001f;
//        final float sun = 1f - ((time * 0.1f) - (int)(time * 0.1f)),
//                blueYellow = TrigTools.sinTurns(sun),
//                greenRed = TrigTools.cosTurns(sun);
//        vision.rememberedOklabColor = DescriptiveColor.oklab(0.45f + blueYellow * 0.15f, blueYellow * 0.02f + 0.5f, greenRed * 0.03f + 0.51f, 1f);

        int rainbow = DescriptiveColor.maximizeSaturation(160,
                (int) (TrigTools.sinTurns(time * 0.5f) * 30f) + 128, (int) (TrigTools.cosTurns(time * 0.5f) * 30f) + 128, 255);

        for (int i = 0; i < toCursor.size(); i++) {
            Coord curr = toCursor.get(i);
            if (vision.inView.contains(curr))
                vision.backgroundColors[curr.x][curr.y] = rainbow;
        }

        float[][] lightLevels = vision.lighting.fovResult;

        for (int x = 0; x < placeWidth; x++) {
            for (int y = 0; y < placeHeight; y++) {
                char glyph = vision.prunedPlaceMap[x][y];
                if (vision.seen.contains(x, y)) {
                    // cells that were seen more than one frame ago, and aren't visible now, appear as a gray memory.
                    batch.setPackedColor(intBitsToFloat(0xFEFFFFFF & vision.backgroundColors[x][y]));
                    if (glyph == '/' || glyph == '+' || glyph == '1' || glyph == '2') // doors expect a floor drawn beneath them
                        batch.draw(charMapping.getOrDefault('.', solid), x, y, 1f, 1f);
                    batch.draw(charMapping.getOrDefault(glyph, solid), x, y, 1f, 1f);
                    // visual debugging; show all cells that were just taken out of view
//                    if(vision.justHidden.contains(x, y)) batch.draw(charMapping.getOrDefault('s', solid), x, y, 1f, 1f);
                }
            }
        }
        AnimatedGlidingSprite monster;

        for (int i = 0; i < placeWidth; i++) {
            for (int j = 0; j < placeHeight; j++) {
                if (lightLevels[i][j] > 0.01) {
                    if ((monster = monsters.get(Coord.get(i, j))) != null) {
                        monster = monster.animate(time);
                        monster.setPackedColor(intBitsToFloat(0xFEFFFFFF & vision.getForegroundColor(i, j, change)));
                        monster.draw(batch);
                    }
                } else if (vision.justHidden.contains(i, j) && (monster = monsters.get(Coord.get(i, j))) != null) {
                    monster = monster.animate(time);
                    monster.setPackedColor(intBitsToFloat(0xFEFFFFFF & vision.getForegroundColor(i, j, change)));
                    monster.draw(batch);
                }
            }
        }
//        batch.setPackedColor(intBitsToFloat(0xFEFFFFFF));
//        playerSprite.setPackedColor(intBitsToFloat(0xFEFFFFFF & vision.getForegroundColor(player.x, player.y, change)));
//        batch.setPackedColor(NEUTRAL_PACKED);
        playerSprite.animate(time).draw(batch);
//        Gdx.graphics.setTitle(Gdx.graphics.getFramesPerSecond() + " FPS");
    }

    /**
     * Supports WASD, vi-keys (hjklyubn), arrow keys, and numpad for movement, plus '.' or numpad 5 to stay still.
     */
    public void handleHeldKeys() {
        float c = playerSprite.location.getChange();
        if (c != 0f && c != 1f) return;
        if (input.isKeyPressed(A) || input.isKeyPressed(H) || input.isKeyPressed(LEFT) || input.isKeyPressed(NUMPAD_4))
            move(Direction.LEFT);
        else if (input.isKeyPressed(S) || input.isKeyPressed(J) || input.isKeyPressed(DOWN) || input.isKeyPressed(NUMPAD_2))
            move(Direction.DOWN);
        else if (input.isKeyPressed(W) || input.isKeyPressed(K) || input.isKeyPressed(UP) || input.isKeyPressed(NUMPAD_8))
            move(Direction.UP);
        else if (input.isKeyPressed(D) || input.isKeyPressed(L) || input.isKeyPressed(RIGHT) || input.isKeyPressed(NUMPAD_6))
            move(Direction.RIGHT);
        else if (input.isKeyPressed(Y) || input.isKeyPressed(NUMPAD_7))
            move(Direction.UP_LEFT);
        else if (input.isKeyPressed(U) || input.isKeyPressed(NUMPAD_9))
            move(Direction.UP_RIGHT);
        else if (input.isKeyPressed(B) || input.isKeyPressed(NUMPAD_1))
            move(Direction.DOWN_LEFT);
        else if (input.isKeyPressed(N) || input.isKeyPressed(NUMPAD_3))
            move(Direction.DOWN_RIGHT);
        else if (input.isKeyPressed(PERIOD) || input.isKeyPressed(NUMPAD_5) || input.isKeyPressed(NUMPAD_DOT))
            move(Direction.NONE);
    }

    private void move(Direction dir) {
        toCursor.clear();
        awaitedMoves.clear();
        awaitedMoves.add(playerSprite.getLocation().getStart().translate(dir));
    }

    @Override
    public void render() {
        if (input.isKeyJustPressed(R))
            restart(lang.hashCode());

        // standard clear the background routine for libGDX
        ScreenUtils.clear(0f, 0f, 0f, 0f);
        // center the camera on the player's position
        camera.position.x = playerSprite.getX();
        camera.position.y = playerSprite.getY();
        camera.update();


        mainViewport.apply(false);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.getShader().setUniformf("u_globalMul", 1f, 0.8f, 0.9f);
        batch.getShader().setUniformf("u_globalAdd", 0f, 0.1f - health * 0.0115f, 0.08f - health * 0.0111f);

        // you done bad. you done real bad.
        if (health <= 0) {
            // still need to display the map, then write over it with a message.
            putMap();
            float wide = mainViewport.getWorldWidth(),
                    x = playerSprite.getX() - mainViewport.getWorldWidth() * 0.5f,
                    y = playerSprite.getY();
            font.draw(batch, "[RED]YOUR CRAWL IS OVER!", x, y + 2, wide, Align.center, true);
            font.draw(batch, "[GRAY]A monster sniffs your corpse and says,", x, y + 1, wide, Align.center, true);
            font.draw(batch, "[FOREST]" + lang, x, y, wide, Align.center, true);
            font.draw(batch, "[GRAY]q to quit.", x, y - 2, wide, Align.center, true);
            font.draw(batch, "[YELLOW]r to restart.", x, y - 4, wide, Align.center, true);
            batch.end();
            if (input.isKeyPressed(Q))
                Gdx.app.exit();
            return;
        }
        playerDirector.step();
        monsterDirector.step();
        directorSmall.step();

        if (phase == Phase.MONSTER_ANIM) {
            if (!monsterDirector.isPlaying()) {
                phase = Phase.WAIT;
                if (!awaitedMoves.isEmpty()) {
                    Coord m = awaitedMoves.removeFirst();
                    if (!toCursor.isEmpty())
                        toCursor.removeFirst();
                    move(m);
                }
            }
        } else if (phase == Phase.WAIT && !awaitedMoves.isEmpty()) {
            Coord m = awaitedMoves.removeFirst();
            if (!toCursor.isEmpty())
                toCursor.removeFirst();
            move(m);
        } else if (phase == Phase.PLAYER_ANIM) {
            if (!playerDirector.isPlaying() && !monsterDirector.isPlaying()) {
                phase = Phase.MONSTER_ANIM;
                afterChange();
                // this only happens if we just removed the last Coord from awaitedMoves, and it's only then that we need to
                // re-calculate the distances from all cells to the player. We don't need to calculate this information on
                // each part of a many-cell move (just the end), nor do we need to calculate it whenever the mouse moves.
                if (awaitedMoves.isEmpty()) {
                    // the next two lines remove any lingering data needed for earlier paths
                    playerToCursor.clearGoals();
                    playerToCursor.resetMap();
                    // the next line marks the player as a "goal" cell, which seems counter-intuitive, but it works because all
                    // cells will try to find the distance between themselves and the nearest goal, and once this is found, the
                    // distances don't change as long as the goals don't change. Since the mouse will move and new paths will be
                    // found, but the player doesn't move until a cell is clicked, the "goal" is the non-changing cell, so the
                    // player's position, and the "target" of a pathfinding method like DijkstraMap.findPathPreScanned() is the
                    // currently-moused-over cell, which we only need to set where the mouse is being handled.
                    playerToCursor.setGoal(player);
                    // DijkstraMap.partialScan only finds the distance to get to a cell if that distance is less than some limit,
                    // which is 13 here. It also won't try to find distances through an impassable cell, which here is the blockage
                    // Region that contains the cells just past the edge of the player's FOV area.
                    playerToCursor.partialScan(13, vision.blockage);
                }
            }
        } else {
            handleHeldKeys();
        }
        putMap();
        pos.set(10, Gdx.graphics.getHeight() - cellHeight - cellHeight);
        mainViewport.unproject(pos);
        font.draw(batch, "[GRAY]Current Health: [RED]" + health + "[WHITE] at "
                + Gdx.graphics.getFramesPerSecond() + " FPS", pos.x, pos.y);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        mainViewport.update(width, height, false);
    }
}
