package teamfour.tasc.model;

import javafx.collections.transformation.FilteredList;
import teamfour.tasc.commons.core.ComponentManager;
import teamfour.tasc.commons.core.LogsCenter;
import teamfour.tasc.commons.core.UnmodifiableObservableList;
import teamfour.tasc.commons.events.model.TaskListChangedEvent;
import teamfour.tasc.commons.util.StringUtil;
import teamfour.tasc.model.task.ReadOnlyTask;
import teamfour.tasc.model.task.Task;
import teamfour.tasc.model.task.UniqueTaskList;
import teamfour.tasc.model.task.UniqueTaskList.TaskNotFoundException;

import java.util.Date;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Represents the in-memory model of the task list data.
 * All changes to any model should be synchronized.
 */
public class ModelManager extends ComponentManager implements Model {
    private static final Logger logger = LogsCenter.getLogger(ModelManager.class);

    private final TaskList taskList;
    private final FilteredList<Task> filteredTasks;
    private PredicateExpression taskListFilter;

    /**
     * Initializes a ModelManager with the given TaskList
     * TaskLIst and its variables should not be null
     */
    public ModelManager(TaskList src, UserPrefs userPrefs) {
        super();
        assert src != null;
        assert userPrefs != null;

        logger.fine("Initializing with task list: " + src + " and user prefs " + userPrefs);

        taskList = new TaskList(src);
        filteredTasks = new FilteredList<>(taskList.getTasks());
        taskListFilter = new PredicateExpression(new AllQualifier());
    }

    public ModelManager() {
        this(new TaskList(), new UserPrefs());
    }

    public ModelManager(ReadOnlyTaskList initialData, UserPrefs userPrefs) {
        taskList = new TaskList(initialData);
        filteredTasks = new FilteredList<>(taskList.getTasks());
        taskListFilter = new PredicateExpression(new AllQualifier());
    }

    @Override
    public void resetData(ReadOnlyTaskList newData) {
        taskList.resetData(newData);
        indicateAddressBookChanged();
    }

    @Override
    public ReadOnlyTaskList getTaskList() {
        return taskList;
    }

    /** Raises an event to indicate the model has changed */
    private void indicateAddressBookChanged() {
        raise(new TaskListChangedEvent(taskList));
    }

    @Override
    public synchronized void deleteTask(ReadOnlyTask target) throws TaskNotFoundException {
        taskList.removeTask(target);
        indicateAddressBookChanged();
    }

    @Override
    public synchronized void addTask(Task task) throws UniqueTaskList.DuplicateTaskException {
        taskList.addTask(task);
        updateFilteredTaskToShowAll();
        indicateAddressBookChanged();
    }

    @Override
    public synchronized void updateTask(ReadOnlyTask oldTask, Task newTask) throws TaskNotFoundException {
        taskList.updateTask(oldTask, newTask);
        updateFilteredTaskToShowAll();
        indicateAddressBookChanged();
    }

    //=========== Filtered Task List Accessors ===============================================================

    @Override
    public UnmodifiableObservableList<ReadOnlyTask> getFilteredTaskList() {
        return new UnmodifiableObservableList<>(filteredTasks);
    }

    @Override
    public void updateFilteredTaskToShowAll() {
        filteredTasks.setPredicate(null);
    }

    @Override
    public void updateFilteredTaskList(Set<String> keywords){
        updateFilteredTaskList(new PredicateExpression(new NameQualifier(keywords)));
    }
    
    @Override
    public void resetTaskListFilter() {
        taskListFilter = new PredicateExpression(new AllQualifier());
    }
    
    @Override
    public void addTaskListFilterByType(String type, boolean negated) {
        taskListFilter.and(new PredicateExpression(new TypeQualifier(type), negated));
    }
    
    @Override
    public void addTaskListFilterByDeadline(Date deadline, boolean negated) {
        taskListFilter.and(new PredicateExpression(new DeadlineQualifier(deadline), negated));
    }
    
    @Override
    public void addTaskListFilterByStartTime(Date startTime, boolean negated) {
        taskListFilter.and(new PredicateExpression(new StartTimeQualifier(startTime), negated));
    }
    
    @Override
    public void addTaskListFilterByEndTime(Date endTime, boolean negated) {
        taskListFilter.and(new PredicateExpression(new EndTimeQualifier(endTime), negated));
    }
    
    @Override
    public void addTaskListFilterByStartToEndTime(Date startTime, Date endTime, boolean negated) {
        taskListFilter.and(new PredicateExpression(new StartToEndTimeQualifier(startTime, endTime), negated));
    }
    
    @Override
    public void addTaskListFilterByTags(Set<String> tags, boolean negated) {
        taskListFilter.and(new PredicateExpression(new TagQualifier(tags), negated));
    }
    
    @Override
    public void updateFilteredTaskListByFilter() {
        updateFilteredTaskList(taskListFilter);
    }

    private void updateFilteredTaskList(Expression expression) {
        filteredTasks.setPredicate(expression::satisfies);
    }

    //========== Inner classes/interfaces used for filtering ==================================================

    interface Expression {
        boolean satisfies(ReadOnlyTask task);
        String toString();
    }

    private class PredicateExpression implements Expression {

        private final Qualifier qualifier;
        private PredicateExpression and;
        private boolean isNegated;

        PredicateExpression(Qualifier qualifier) {
            this(qualifier, false);
        }
        
        PredicateExpression(Qualifier qualifier, boolean negated) {
            this.qualifier = qualifier;
            this.and = null;
            this.isNegated = negated;
        }
        
        /**
         * Chains the predicate using logical AND of this predicate and another.
         * @param and The other predicate
         */
        public void and(PredicateExpression other) {
            PredicateExpression tail = this;
            while (tail.and != null) {
                tail = tail.and;
            }
            tail.and = other;
        }

        @Override
        /**
         * Runs all the chained predicates using logical AND.
         * @param task Task to check
         * @return true if all predicates are satisfied
         */
        public boolean satisfies(ReadOnlyTask task) {
            PredicateExpression it = this;
            while (it != null) {
                if (it.qualifier.run(task) == it.isNegated) {
                    return false;
                }
                it = it.and;
            }
            return true;
        }

        @Override
        public String toString() {
            return qualifier.toString();
        }
    }

    interface Qualifier {
        boolean run(ReadOnlyTask task);
        String toString();
    }
    
    private class AllQualifier implements Qualifier {
        AllQualifier() {}

        @Override
        public boolean run(ReadOnlyTask task) {
            return true;
        }

        @Override
        public String toString() {
            return "all qualifier";
        }
    }

    private class NameQualifier implements Qualifier {
        private Set<String> nameKeyWords;

        NameQualifier(Set<String> nameKeyWords) {
            this.nameKeyWords = nameKeyWords;
        }

        @Override
        public boolean run(ReadOnlyTask task) {
            return nameKeyWords.stream()
                    .filter(keyword -> StringUtil.containsIgnoreCase(task.getName().getName(), keyword))
                    .findAny()
                    .isPresent();
        }

        @Override
        public String toString() {
            return "name=" + String.join(", ", nameKeyWords);
        }
    }
    
    private class TypeQualifier implements Qualifier {
        private String type;

        TypeQualifier(String type) {
            this.type = type;
        }

        @Override
        public boolean run(ReadOnlyTask task) {
            String[] typeWords = type.split(" ");
            String taskType = task.getAsType();
            
            for (String typeWord : typeWords) {
                if (!StringUtil.containsIgnoreCase(taskType, typeWord))
                    return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "type=" + type;
        }
    }
    
    private class DeadlineQualifier implements Qualifier {
        private Date deadline;

        DeadlineQualifier(Date deadline) {
            this.deadline = deadline;
        }

        @Override
        public boolean run(ReadOnlyTask task) {
            if (task.getDeadline().hasDeadline() == false) {
                return false;
            }
            return deadline.after(task.getDeadline().getDeadline());
        }

        @Override
        public String toString() {
            return "deadline=" + deadline;
        }
    }
    
    private class StartTimeQualifier implements Qualifier {
        private Date startTime;

        StartTimeQualifier(Date startTime) {
            this.startTime = startTime;
        }

        @Override
        public boolean run(ReadOnlyTask task) {
            if (task.getPeriod().hasPeriod()) {
                return startTime.before(task.getPeriod().getEndTime());
            } else if (task.getDeadline().hasDeadline()) {
                return startTime.before(task.getDeadline().getDeadline());
            }
            return true;
        }

        @Override
        public String toString() {
            return "startTime=" + startTime;
        }
    }
    
    private class EndTimeQualifier implements Qualifier {
        private Date endTime;

        EndTimeQualifier(Date endTime) {
            this.endTime = endTime;
        }

        @Override
        public boolean run(ReadOnlyTask task) {
            if (task.getPeriod().hasPeriod()) {
                return endTime.after(task.getPeriod().getStartTime());
            } else if (task.getDeadline().hasDeadline()) {
                return endTime.after(task.getDeadline().getDeadline());
            }
            return true;
        }

        @Override
        public String toString() {
            return "endTime=" + endTime;
        }
    }
    
    private class StartToEndTimeQualifier implements Qualifier {
        private Date startTime;
        private Date endTime;

        StartToEndTimeQualifier(Date startTime, Date endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        public boolean run(ReadOnlyTask task) {
            if (task.getPeriod().hasPeriod()) {
                return startTime.before(task.getPeriod().getEndTime()) &&
                        endTime.after(task.getPeriod().getStartTime());
            } else if (task.getDeadline().hasDeadline()) {
                return startTime.before(task.getDeadline().getDeadline()) &&
                        endTime.after(task.getDeadline().getDeadline());
            }
            return false;
        }

        @Override
        public String toString() {
            return "startTime=" + startTime + ",endTime=" + endTime;
        }
    }
    
    private class TagQualifier implements Qualifier {
        private Set<String> tagNames;

        TagQualifier(Set<String> tagNames) {
            this.tagNames = tagNames;
        }

        @Override
        public boolean run(ReadOnlyTask task) {
            String taskTagsString = task.tagsString();
            taskTagsString = taskTagsString.replace("[", "");
            taskTagsString = taskTagsString.replace("]", "");
            String source = taskTagsString.replace(",", "");
            return tagNames.stream()
                    .filter(tagName -> StringUtil.containsIgnoreCase(source, tagName))
                    .findAny()
                    .isPresent();
        }

        @Override
        public String toString() {
            return "tags=" + String.join(", ", tagNames);
        }
    }
}