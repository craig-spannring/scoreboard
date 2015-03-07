$(initialize);

function initialize() {
	WS.Connect();
	WS.AutoRegister();

	// Set Styles
	var view = "View";
	if (_windowFunctions.checkParam("preview", "true"))
		view = "Preview";
	WS.Register( "ScoreBoard.Setting(ScoreBoard." + view + "_SwapTeams)", function (k, v) {
		$(".Team1").toggleClass("Left", !isTrue(v)).toggleClass("Right", isTrue(v));
		$(".Team2").toggleClass("Left", isTrue(v)).toggleClass("Right", !isTrue(v));
		$(".Team").toggleClass("Swapped", isTrue(v));
	});

	WS.Register( [ "ScoreBoard.Setting(ScoreBoard." + view + "_BoxStyle)",
		"ScoreBoard.Setting(ScoreBoard." + view + "_BackgroundStyle)",
		"ScoreBoard.Setting(ScoreBoard." + view + "_HideJamTotals)",
		"ScoreBoard.Setting(ScoreBoard." + view + "_SidePadding)" ], function(k, v) {
			var boxStyle = WS.state["ScoreBoard.Setting(ScoreBoard." + view + "_BoxStyle)"];
			var backgroundStyle = WS.state["ScoreBoard.Setting(ScoreBoard." + view + "_BackgroundStyle)"];
			var showJamTotals = !isTrue(WS.state["ScoreBoard.Setting(ScoreBoard." + view + "_HideJamTotals)"]);
			var sidePadding = WS.state["ScoreBoard.Setting(ScoreBoard." + view + "_SidePadding)"];

			$("body").removeClass();
			if (boxStyle != "" && boxStyle != null)
				$("body").addClass(boxStyle);
			if (backgroundStyle != "" && backgroundStyle != null)
				$("body").addClass(backgroundStyle);
			$("#sb").toggleClass("JamScore", showJamTotals);

			left = 0;
			right = 0;
			if (sidePadding != "" && sidePadding != null) {
				left = sidePadding;
				right = left;
			}
			$("#sb").css({ "left": left + "%", "width": (100 - left - right) + "%" });
			$(window).trigger("resize");
	});

	/*
	var v = $sb("ScoreBoard.Settings.Setting(ScoreBoard." + view + "_CurrentView)");
	v.$sbBindAndRun("sbchange", function(event, value) {
		console.log(this, value);
		$("video").each(function() { this.pause(); });
		var showDiv = $("#"+value+"Div");
		if (!showDiv.length)
			showDiv = $("#sbDiv");
		showDiv.children("video").each(function() { this.currentTime = 0; this.play(); });
		$("#mainDiv>div.View").not(showDiv).removeClass("Show");
		showDiv.addClass("Show");
	});
	*/

	// Show Clocks
	WS.Register( [
		"ScoreBoard.Clock(Period).Running",
		"ScoreBoard.Clock(Jam).Running",
		"ScoreBoard.Clock(Lineup).Running",
		"ScoreBoard.Clock(Timeout).Running",
		"ScoreBoard.Clock(Intermission).Running" ], function(k, v) {
			var lc = WS.state["ScoreBoard.Clock(Lineup).Running"];
			var tc = WS.state["ScoreBoard.Clock(Timeout).Running"];
			var ic = WS.state["ScoreBoard.Clock(Intermission).Running"];

			var clock = "Jam";
			if (isTrue(tc))
				clock = "Timeout";
			else if (isTrue(lc))
				clock = "Lineup";
			else if (isTrue(ic))
				clock = "Intermission";
			$(".Clock").removeClass("Show");
			$(".Clock.ShowIn" + clock).addClass("Show");
	});
}

function jammer(k, v) {
	id = getTeamId(k);
	var prefix = "ScoreBoard.Team(" + id + ").";
	var jammerId = WS.state[prefix + "Position(Jammer).Skater"];
	var jammerName = WS.state[prefix + "Skater(" + jammerId + ").Name"];
	var pivotId = WS.state[prefix + "Position(Pivot).Skater"];
	var pivotName = WS.state[prefix + "Skater(" + pivotId + ").Name"];
	var leadJammer = (WS.state[prefix + "LeadJammer"] === "Lead");
	var starPass = isTrue(WS.state[prefix + "StarPass"]);

	if (jammerName == null)
		jammerName = leadJammer ? "Lead" : "";
	if (pivotName == null)
		pivotName = "";

	$(".Team" + id + " .Lead").toggleClass("HasLead", (leadJammer && !starPass));
	return !starPass ? jammerName : pivotName;
}

function getTeamId(k) {
	if (k.indexOf("Team(1)") > 0)
		return "1";
	if (k.indexOf("Team(2)") > 0)
		return "2";
	return null;
}

function nameUpdate(k, v) {
	id = getTeamId(k);
	var prefix = "ScoreBoard.Team(" + id + ").";
	var name = WS.state[prefix + "Name"];
	var altName = WS.state[prefix + "AlternateName(scoreboard)"];
	if (altName != null && altName != "")
		name = altName;

	$(".Team" + id).toggleClass("HasName", name != "");
	return name;
}

function logoUpdate(k, v) {
	id = getTeamId(k);
	var prefix = "ScoreBoard.Team(" + id + ").";
	var logo = WS.state[prefix + "Logo"];
	if (logo == null)
		logo = "";
	if (logo != "")
		logo = "url(" + logo + ")";

	$(".Team" + id + ">.Logo").css("background-image", logo);
	$(".Team" + id).toggleClass("HasLogo", logo != "");
	var nameAutoFit = $(".Team" + id + ">.Name>div").data("AutoFit");
	if (nameAutoFit)
		nameAutoFit();
}

function smallDescriptionUpdate(k, v) {
	var lc = WS.state["ScoreBoard.Clock(Lineup).Running"];
	var tc = WS.state["ScoreBoard.Clock(Timeout).Running"];
	var to = WS.state["ScoreBoard.TimeoutOwner"];
	var or = WS.state["ScoreBoard.OfficialReview"];
	var lcn = WS.state["ScoreBoard.Clock(Lineup).Name"];
	var tcn = WS.state["ScoreBoard.Clock(Timeout).Name"];
	var ret = '';

	$.each(["1", "2"], function (idx, id) {
		tto = WS.state["ScoreBoard.Team(" + id + ").Timeouts"];
		tor = WS.state["ScoreBoard.Team(" + id + ").OfficialReviews"];
		$(".Team" + id + " .Timeout1").toggleClass("Used", tto < 1);
		$(".Team" + id + " .Timeout2").toggleClass("Used", tto < 2);
		$(".Team" + id + " .Timeout3").toggleClass("Used", tto < 3);
		$(".Team" + id + " .OfficialReview1").toggleClass("Used", tor < 1);
	});

	$(".Team .Dot").removeClass("Active");
	$(".Clock.Description,.Team>.Timeouts,.Team>.OfficialReviews").removeClass("Red");
	if (lc)
		ret = lcn;
	else if (tc) {
		$(".Clock.Description").addClass("Red");

		ret = tcn;
		if (to != "" && !or) {
			ret = "Team Timeout";
			$(".Team" + to + ">.Timeouts").addClass("Red");
			var dotSel = ".Team" + to + " .Timeout" + (WS.state["ScoreBoard.Team(" + to + ").Timeouts"] + 1);
			$(dotSel).addClass("Active");
		}
		if (to != "" && or) {
			ret = "Official Review";
			$(".Team" + to + ">.OfficialReviews:not(.Header)").addClass("Red");
			var dotSel = ".Team" + to + " .OfficialReview" + (WS.state["ScoreBoard.Team(" + to + ").OfficialReviews"] + 1);
			$(dotSel).addClass("Active");
		}
	}
	return ret;
}

function intermissionDisplay() {
	var num = WS.state["ScoreBoard.Clock(Intermission).Number"];
	var max = WS.state["ScoreBoard.Clock(Intermission).MaximumNumber"];
	var isOfficial = WS.state["ScoreBoard.OfficialScore"];
	var ret = '';
	if (num == 0)
		ret = WS.state["ScoreBoard.Setting(Intermission.PreGame)"];
	else if (num != max)
		ret = WS.state["ScoreBoard.Setting(Intermission.Intermission)"];
	else if (!isOfficial)
		ret = WS.state["ScoreBoard.Setting(Intermission.Unofficial)"];
	else
		ret = WS.state["ScoreBoard.Setting(Intermission.Official)"];

	$(".Clock.Intermission .Time").toggleClass("Hide", num == max);
	return ret;
}

function toClockInitialNumber(k, v) {
	var ret = '';
	$.each(["Period", "Jam"], function (i, c) {
		if (k.indexOf("Clock(" + c + ")") > -1) {
			var name = WS.state["ScoreBoard.Clock(" + c + ").Name"];
			var number = WS.state["ScoreBoard.Clock(" + c + ").Number"];
			if (name != null && number != null)
				ret = name.substring(0, 1) + number;
		}
	});
	return ret;
}

function toTime(k, v) {
	return _timeConversions.msToMinSecNoZero(v);
}

function toInitial(k, v) {
	return v == null ? '' : v.substring(0, 1);
}
