package guitests;

import guitests.guihandles.TaskCardHandle;
import org.junit.Test;

import teamfour.tasc.commons.core.Messages;
import teamfour.tasc.logic.commands.AddCommand;
import teamfour.tasc.testutil.TestTask;
import teamfour.tasc.testutil.TestUtil;
import teamfour.tasc.testutil.TypicalTestTasks;

import static org.junit.Assert.assertTrue;

public class AddCommandTest extends TaskListGuiTest {

    @Test
    public void add() {

        commandBox.runCommand("list all");

        //add one task
        TestTask[] currentList = td.getTypicalTasks();
        TestTask taskToAdd = TypicalTestTasks.attendWorkshop;
        assertAddSuccess(taskToAdd, currentList);
        currentList = TestUtil.addTasksToList(currentList, taskToAdd);

        //add another task
        taskToAdd = TypicalTestTasks.updateGithubRepo;
        assertAddSuccess(taskToAdd, currentList);
        currentList = TestUtil.addTasksToList(currentList, taskToAdd);

        //add duplicate task
        commandBox.runCommand(TypicalTestTasks.attendWorkshop.getAddCommand());
        assertResultMessage(AddCommand.MESSAGE_DUPLICATE_TASK);
        assertTrue(taskListPanel.isListMatching(currentList));

        //add to empty list
        commandBox.runCommand("clear");
        assertAddSuccess(TypicalTestTasks.submitPrototype);

        //invalid command
        commandBox.runCommand("adds Johnny");
        assertResultMessage(Messages.MESSAGE_UNKNOWN_COMMAND);
    }
    //@@author A0127014W
    @Test
    public void add_taskWithInvalidDates_failure(){
        String inputCommand = "add validTask from invalidDate to invalidDate";
        commandBox.runCommand(inputCommand);
        assertResultMessage(AddCommand.MESSAGE_INVALID_DATES);

        inputCommand = "add validTask from invalidDate by today";
        commandBox.runCommand(inputCommand);
        assertResultMessage(AddCommand.MESSAGE_INVALID_DATES);

        inputCommand = "add validTask by invalidDate";
        commandBox.runCommand(inputCommand);
        assertResultMessage("Invalid date");
    }
    //@@author A0127014W
    private void assertAddSuccess(TestTask taskToAdd, TestTask... currentList) {
        commandBox.runCommand(taskToAdd.getAddCommand());

        //confirm the new card contains the right data
        TaskCardHandle addedCard = taskListPanel.navigateToTask(taskToAdd.getName().getName());
        assertMatching(taskToAdd, addedCard);

        //confirm the list now contains all previous tasks plus the new task
        TestTask[] expectedList = TestUtil.addTasksToList(currentList, taskToAdd);
        assertTrue(taskListPanel.isListMatching(expectedList));
    }

}
